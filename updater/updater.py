#!/usr/bin/env python3
"""アプリケーション内の drain 後に koemoji の Compose service を更新する。"""

from __future__ import annotations

import fcntl
import json
import logging
import os
import re
import subprocess
import tempfile
import time
import urllib.error
import urllib.request
import uuid
from dataclasses import dataclass
from pathlib import Path
from typing import Any, Callable


LOG = logging.getLogger("koemoji-updater")
VERSION_RE = re.compile(r"^v?(0|[1-9]\d*)\.(0|[1-9]\d*)\.(0|[1-9]\d*)$")
AUTOUPDATE_LABEL = "io.koemoji.autoupdate"
OCI_VERSION_LABEL = "org.opencontainers.image.version"
DEFAULT_IMAGE = "ghcr.io/jaoafa/koemoji:latest"
DEFAULT_API = "https://api.github.com/repos/jaoafa/koemoji/releases/latest"
READY_PHASE = "READY"


class UpdateError(RuntimeError):
    """安全に更新を続行できない場合に送出する。"""


@dataclass(frozen=True)
class Service:
    name: str
    mode: str
    image: str
    health_port: int


def parse_version(value: str) -> tuple[int, int, int] | None:
    match = VERSION_RE.fullmatch(value)
    return tuple(map(int, match.groups())) if match else None


def image_is_latest(image: str, expected: str = DEFAULT_IMAGE) -> bool:
    if "@" in image:
        return False
    expected_name = expected.rsplit(":", 1)[0] if ":" in expected.rsplit("/", 1)[-1] else expected
    name = image.rsplit(":", 1)[0] if ":" in image.rsplit("/", 1)[-1] else image
    tag = image.rsplit(":", 1)[1] if ":" in image.rsplit("/", 1)[-1] else "latest"
    return name == expected_name and tag == "latest"


def image_repository(image: str) -> str:
    image = image.split("@", 1)[0]
    last = image.rsplit("/", 1)[-1]
    return image.rsplit(":", 1)[0] if ":" in last else image


def select_services(config: dict[str, Any], expected_image: str = DEFAULT_IMAGE) -> list[Service]:
    services: list[Service] = []
    for name, raw in config.get("services", {}).items():
        labels = raw.get("labels", {}) or {}
        if labels.get(AUTOUPDATE_LABEL) != "true":
            continue
        environment = raw.get("environment", {}) or {}
        mode = str(environment.get("MODE", "all")).lower()
        image = str(raw.get("image", ""))
        try:
            port = int(environment.get("HEALTH_PORT", "8080"))
        except (TypeError, ValueError) as exc:
            raise UpdateError(f"Service {name} has invalid HEALTH_PORT") from exc
        if mode not in {"all", "capture", "worker"}:
            raise UpdateError(f"Service {name} has unsupported MODE")
        if port <= 0 or port > 65535:
            raise UpdateError(f"Service {name} has an invalid health port")
        services.append(Service(name, mode, image, port))

    if not services:
        return []
    if len({service.image for service in services}) != 1:
        raise UpdateError("Auto-update services must use the same image reference")
    image = services[0].image
    expected_repository = image_repository(expected_image)
    if image_repository(image) != expected_repository:
        raise UpdateError("Auto-update services do not use the supported image")
    if not image_is_latest(image, expected_image):
        return []
    if any(service.mode == "all" for service in services) and any(service.mode != "all" for service in services):
        raise UpdateError("MODE=all cannot be combined with split capture/worker services")
    if not any(service.mode in {"all", "capture"} for service in services):
        raise UpdateError("At least one capture or all-mode service is required")
    if any(service.mode == "capture" for service in services) and not any(service.mode == "worker" for service in services):
        raise UpdateError("MODE=capture requires a worker service")
    return services


class Journal:
    def __init__(self, path: Path):
        self.path = path
        self.path.parent.mkdir(parents=True, exist_ok=True)

    def read(self) -> dict[str, Any] | None:
        try:
            return json.loads(self.path.read_text(encoding="utf-8"))
        except FileNotFoundError:
            return None

    def write(self, transaction: dict[str, Any]) -> None:
        fd, name = tempfile.mkstemp(prefix="transaction.", dir=self.path.parent)
        try:
            with os.fdopen(fd, "w", encoding="utf-8") as stream:
                json.dump(transaction, stream, sort_keys=True)
                stream.flush()
                os.fsync(stream.fileno())
            os.chmod(name, 0o600)
            os.replace(name, self.path)
            directory_fd = os.open(self.path.parent, os.O_DIRECTORY)
            try:
                os.fsync(directory_fd)
            finally:
                os.close(directory_fd)
        finally:
            if os.path.exists(name):
                os.unlink(name)

    def clear(self) -> None:
        self.path.unlink(missing_ok=True)


class Compose:
    def __init__(self, project_dir: Path, compose_file: Path, runner: Callable[..., subprocess.CompletedProcess] | None = None):
        self.project_dir = project_dir
        self.compose_file = compose_file
        self.runner = runner or subprocess.run

    def run(self, *args: str) -> str:
        command = [
            "docker", "compose", "--project-directory", str(self.project_dir),
            "-f", str(self.compose_file), *args,
        ]
        result = self.runner(command, cwd=self.project_dir, text=True, stdout=subprocess.PIPE,
                             stderr=subprocess.PIPE, check=False)
        if result.returncode != 0:
            raise UpdateError(f"Docker Compose command failed ({args[0] if args else 'unknown'}, exit {result.returncode})")
        return result.stdout

    def config(self) -> dict[str, Any]:
        return json.loads(self.run("config", "--format", "json"))

    def container_ids(self, service: str) -> list[str]:
        output = self.run("ps", "--all", "--quiet", service)
        return [line.strip() for line in output.splitlines() if line.strip()]

    def inspect(self, container_ids: list[str]) -> list[dict[str, Any]]:
        if not container_ids:
            return []
        command = ["docker", "inspect", *container_ids]
        result = self.runner(command, cwd=self.project_dir, text=True, stdout=subprocess.PIPE,
                             stderr=subprocess.PIPE, check=False)
        if result.returncode != 0:
            raise UpdateError(f"Docker inspect failed (exit {result.returncode})")
        return json.loads(result.stdout)

    def image_labels(self, image_id: str) -> dict[str, str]:
        command = ["docker", "image", "inspect", image_id]
        result = self.runner(command, cwd=self.project_dir, text=True, stdout=subprocess.PIPE,
                             stderr=subprocess.PIPE, check=False)
        if result.returncode != 0:
            raise UpdateError(f"Docker image inspect failed (exit {result.returncode})")
        return json.loads(result.stdout)[0].get("Config", {}).get("Labels", {}) or {}

    def current_images(self, services: list[Service]) -> dict[str, dict[str, str]]:
        result: dict[str, dict[str, str]] = {}
        for service in services:
            containers = self.inspect(self.container_ids(service.name))
            if not containers:
                raise UpdateError(f"No running container found for service {service.name}")
            image_ids = {container.get("Image") for container in containers}
            if len(image_ids) != 1 or None in image_ids:
                raise UpdateError(f"Service {service.name} has inconsistent container images")
            image_id = next(iter(image_ids))
            labels = self.image_labels(image_id)
            version = labels.get(OCI_VERSION_LABEL, "")
            if not parse_version(version):
                raise UpdateError(f"Service {service.name} image has no valid OCI version label")
            result[service.name] = {"image_id": image_id, "version": version}
        if len({entry["version"] for entry in result.values()}) != 1:
            raise UpdateError("Auto-update services are running different versions")
        if len({entry["image_id"] for entry in result.values()}) != 1:
            raise UpdateError("Auto-update services are running different image digests")
        return result

    def image_metadata(self, image: str) -> dict[str, Any]:
        command = ["docker", "image", "inspect", image]
        result = self.runner(command, cwd=self.project_dir, text=True, stdout=subprocess.PIPE,
                             stderr=subprocess.PIPE, check=False)
        if result.returncode != 0:
            raise UpdateError(f"Pulled image inspect failed (exit {result.returncode})")
        metadata = json.loads(result.stdout)[0]
        return {
            "image_id": metadata["Id"],
            "version": (metadata.get("Config", {}).get("Labels", {}) or {}).get(OCI_VERSION_LABEL, ""),
            "repo_digests": metadata.get("RepoDigests", []),
        }

    def pull(self, services: list[Service]) -> None:
        self.run("pull", "--quiet", *(service.name for service in services))

    def up(self, services: list[Service]) -> None:
        self.run("up", "--detach", "--no-deps", "--pull", "never", *(service.name for service in services))

    def kill(self, services: list[Service]) -> None:
        if services:
            self.run("kill", "--signal", "SIGKILL", *(service.name for service in services))

    def tag(self, image_id: str, image_ref: str) -> None:
        command = ["docker", "image", "tag", image_id, image_ref]
        result = self.runner(command, cwd=self.project_dir, text=True, stdout=subprocess.PIPE,
                             stderr=subprocess.PIPE, check=False)
        if result.returncode != 0:
            raise UpdateError(f"Docker image retag failed (exit {result.returncode})")


class Updater:
    def __init__(self, compose: Compose, journal: Journal, secret: str, release_url: str = DEFAULT_API,
                 image_ref: str = DEFAULT_IMAGE, health_timeout: int = 180, drain_timeout: int = 65,
                 opener: Callable[..., Any] | None = None, sleep: Callable[[float], None] = time.sleep):
        self.compose = compose
        self.journal = journal
        self.secret = secret
        self.release_url = release_url
        self.image_ref = image_ref
        self.health_timeout = health_timeout
        self.drain_timeout = drain_timeout
        self.opener = opener or urllib.request.urlopen
        self.sleep = sleep

    def latest_release(self) -> str | None:
        request = urllib.request.Request(self.release_url, headers={
            "Accept": "application/vnd.github+json",
            "User-Agent": "koemoji-updater",
            "X-GitHub-Api-Version": "2022-11-28",
        })
        try:
            with self.opener(request, timeout=15) as response:
                release = json.load(response)
        except urllib.error.HTTPError as exc:
            if exc.code == 404:
                return None
            raise UpdateError(f"GitHub release lookup failed (HTTP {exc.code})") from exc
        except (OSError, ValueError) as exc:
            raise UpdateError(f"GitHub release lookup failed ({type(exc).__name__})") from exc
        if release.get("draft") or release.get("prerelease"):
            return None
        tag = str(release.get("tag_name", ""))
        if not parse_version(tag):
            raise UpdateError("Latest GitHub release tag is not a stable semantic version")
        return tag

    def check(self) -> bool:
        if not self.secret:
            LOG.warning("Automatic updates are disabled because UPDATE_CONTROL_SECRET is empty")
            return False
        config = self.compose.config()
        services = select_services(config, self.image_ref)
        if not services:
            LOG.info("No latest-tag koemoji service is configured; skipping updates")
            return False
        current = self.compose.current_images(services)
        release = self.latest_release()
        if release is None:
            return False
        current_version = next(iter(current.values()))["version"]
        if parse_version(current_version) >= parse_version(release):
            return False
        state = {
            "id": str(uuid.uuid4()),
            "release": release,
            "image": self.image_ref,
            "services": [service.__dict__ for service in services],
            "previous": current,
            "phase": "PULLING",
            "created_at": int(time.time()),
        }
        self.journal.write(state)
        return self.resume(state)

    def resume(self, transaction: dict[str, Any]) -> bool:
        services = [Service(**service) for service in transaction["services"]]
        try:
            while True:
                phase = transaction["phase"]
                if phase == "PULLING":
                    self.compose.pull(services)
                    target = self.compose.image_metadata(transaction["image"])
                    if target["version"] != transaction["release"]:
                        raise UpdateError("Pulled latest image version does not match the latest GitHub release")
                    transaction["target"] = target
                    self._phase(transaction, "DRAINING_CAPTURE")
                elif phase == "DRAINING_CAPTURE":
                    capture = [service for service in services if service.mode in {"all", "capture"}]
                    if not self._drain(capture):
                        self._cancel_drain(services)
                        self.journal.clear()
                        return False
                    self._phase(transaction, "DRAINING_WORKERS")
                elif phase == "DRAINING_WORKERS":
                    workers = [service for service in services if service.mode == "worker"]
                    if workers and not self._drain(workers):
                        self._cancel_drain(services)
                        self.journal.clear()
                        return False
                    self._phase(transaction, "RECREATING")
                elif phase == "RECREATING":
                    self.compose.kill(services)
                    self.compose.up(services)
                    self._phase(transaction, "HEALTHCHECK")
                elif phase == "HEALTHCHECK":
                    if not self._healthy(services, transaction["target"]["image_id"]):
                        self._phase(transaction, "ROLLBACK")
                    else:
                        self._phase(transaction, "DONE")
                elif phase == "ROLLBACK":
                    self._rollback(transaction, services)
                    self._phase(transaction, "ROLLBACK_HEALTHCHECK")
                elif phase == "ROLLBACK_HEALTHCHECK":
                    previous = {entry["image_id"] for entry in transaction["previous"].values()}
                    if not self._healthy(services, expected_image_ids=previous):
                        raise UpdateError("Rollback image did not become healthy")
                    self._phase(transaction, "DONE")
                elif phase == "DONE":
                    self.journal.clear()
                    return True
                else:
                    raise UpdateError("Transaction journal contains an unknown phase")
        except UpdateError:
            LOG.exception("Update transaction %s remains at phase %s", transaction.get("id"), transaction.get("phase"))
            return False

    def _phase(self, transaction: dict[str, Any], phase: str) -> None:
        transaction["phase"] = phase
        self.journal.write(transaction)

    def _url(self, service: Service, path: str) -> str:
        return f"http://{service.name}:{service.health_port}{path}"

    def _api(self, service: Service, path: str, method: str = "GET") -> dict[str, Any] | None:
        request = urllib.request.Request(self._url(service, path), method=method, headers={
            "X-Koemoji-Update-Secret": self.secret,
        })
        try:
            with self.opener(request, timeout=3) as response:
                if response.status in {200, 202}:
                    body = response.read()
                    return json.loads(body) if body and "json" in response.headers.get("Content-Type", "") else None
                raise UpdateError(f"Service {service.name} returned HTTP {response.status} for update control")
        except urllib.error.HTTPError as exc:
            exc.close()
            raise UpdateError(f"Service {service.name} rejected update control (HTTP {exc.code})") from exc
        except (OSError, urllib.error.URLError, ValueError):
            return None

    def _drain(self, services: list[Service]) -> bool:
        if not services:
            return True
        deadline = time.monotonic() + self.drain_timeout
        while time.monotonic() < deadline:
            states = [self._api(service, "/update/status") for service in services]
            if any(state and state.get("phase") == "FAILED" for state in states):
                LOG.error("Application reported a failed drain; automatic update canceled")
                self._cancel_drain(services)
                return False
            if all(state and state.get("phase") == READY_PHASE for state in states):
                return True
            for service, state in zip(services, states):
                if state and state.get("phase") == "RUNNING":
                    self._api(service, "/update/drain", "POST")
            self.sleep(0.5)
        return False

    def _cancel_drain(self, services: list[Service]) -> None:
        for service in services:
            self._api(service, "/update/cancel", "POST")

    def _healthy(self, services: list[Service], expected_image_id: str | None = None,
                 expected_image_ids: set[str] | None = None) -> bool:
        deadline = time.monotonic() + self.health_timeout
        while time.monotonic() < deadline:
            all_healthy = True
            for service in services:
                containers = self.compose.inspect(self.compose.container_ids(service.name))
                if not containers:
                    all_healthy = False
                    break
                for container in containers:
                    if container.get("State", {}).get("Status") != "running":
                        all_healthy = False
                        break
                    image_id = container.get("Image")
                    if expected_image_id and image_id != expected_image_id:
                        all_healthy = False
                        break
                    if expected_image_ids and image_id not in expected_image_ids:
                        all_healthy = False
                        break
                if not all_healthy:
                    break
                if not self._health_request(service):
                    all_healthy = False
                    break
            if all_healthy:
                return True
            self.sleep(1)
        return False

    def _health_request(self, service: Service) -> bool:
        request = urllib.request.Request(self._url(service, "/health"), headers={"User-Agent": "koemoji-updater"})
        try:
            with self.opener(request, timeout=3) as response:
                return response.status == 200 and response.read().strip() == b"ok"
        except (OSError, urllib.error.URLError):
            return False

    def _rollback(self, transaction: dict[str, Any], services: list[Service]) -> None:
        for entry in transaction["previous"].values():
            self.compose.tag(entry["image_id"], transaction["image"])
        self.compose.kill(services)
        self.compose.up(services)


def main() -> None:
    logging.basicConfig(level=os.environ.get("LOG_LEVEL", "INFO"), format="%(asctime)s %(levelname)s %(message)s")
    project_dir = Path(os.environ.get("UPDATER_PROJECT_DIR", "/compose")).resolve()
    compose_file = Path(os.environ.get("UPDATER_COMPOSE_FILE", str(project_dir / "compose.yaml"))).resolve()
    journal = Journal(Path(os.environ.get("UPDATER_STATE_FILE", "/var/lib/koemoji-updater/transaction.json")))
    compose = Compose(project_dir, compose_file)
    updater = Updater(
        compose,
        journal,
        os.environ.get("UPDATE_CONTROL_SECRET", ""),
        release_url=os.environ.get("UPDATER_RELEASE_URL", DEFAULT_API),
        image_ref=os.environ.get("UPDATER_IMAGE", DEFAULT_IMAGE),
        health_timeout=int(os.environ.get("UPDATER_HEALTH_TIMEOUT_SECONDS", "180")),
        drain_timeout=int(os.environ.get("UPDATER_DRAIN_TIMEOUT_SECONDS", "65")),
    )
    lock_path = journal.path.with_suffix(".lock")
    lock_path.parent.mkdir(parents=True, exist_ok=True)
    with lock_path.open("a+") as lock:
        fcntl.flock(lock, fcntl.LOCK_EX | fcntl.LOCK_NB)
        while True:
            transaction = journal.read()
            if transaction:
                updater.resume(transaction)
                updater.sleep(60)
                continue
            try:
                updater.check()
                updater.sleep(600)
            except UpdateError:
                LOG.exception("Update check failed")
                updater.sleep(60)


if __name__ == "__main__":
    main()
