import json
import tempfile
import unittest
import urllib.error
from pathlib import Path

from updater.updater import (
    DEFAULT_IMAGE,
    Journal,
    Service,
    UpdateError,
    Updater,
    image_is_latest,
    parse_version,
    select_services,
)


class Response:
    def __init__(self, payload, status=200, content_type="application/json"):
        self.payload = payload
        self.status = status
        self.headers = {"Content-Type": content_type}

    def __enter__(self):
        return self

    def __exit__(self, *_):
        return None

    def read(self):
        return self.payload if isinstance(self.payload, bytes) else json.dumps(self.payload).encode()


def config_for(*services):
    return {"services": {service["name"]: service for service in services}}


def service(name="koemoji", mode="all", image=DEFAULT_IMAGE):
    return {
        "name": name,
        "image": image,
        "labels": {"io.koemoji.autoupdate": "true"},
        "environment": {"MODE": mode, "HEALTH_PORT": "8080"},
    }


class UpdaterTests(unittest.TestCase):
    def test_stable_semver_only(self):
        self.assertEqual((2, 4, 0), parse_version("v2.4.0"))
        self.assertIsNone(parse_version("v2.4.0-rc.1"))
        self.assertIsNone(parse_version("latest"))
        self.assertIsNone(parse_version("v02.4.0"))

    def test_latest_image_detection(self):
        self.assertTrue(image_is_latest("ghcr.io/jaoafa/koemoji:latest"))
        self.assertTrue(image_is_latest("ghcr.io/jaoafa/koemoji"))
        self.assertFalse(image_is_latest("ghcr.io/jaoafa/koemoji:v2.4.0"))
        self.assertFalse(image_is_latest("ghcr.io/jaoafa/koemoji@sha256:" + "a" * 64))
        self.assertFalse(image_is_latest("ghcr.io/other/koemoji:latest"))

    def test_selects_all_mode_latest_service(self):
        selected = select_services(config_for(service()))
        self.assertEqual([Service("koemoji", "all", DEFAULT_IMAGE, 8080)], selected)

    def test_split_mode_requires_worker(self):
        with self.assertRaises(UpdateError):
            select_services(config_for(service(mode="capture")))
        selected = select_services(config_for(service(mode="capture"), service("worker", "worker")))
        self.assertEqual(["capture", "worker"], [item.mode for item in selected])

    def test_pinned_services_are_not_selected(self):
        self.assertEqual([], select_services(config_for(service(image="ghcr.io/jaoafa/koemoji:v2.4.0"))))

    def test_journal_is_atomic_and_private(self):
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "transaction.json"
            journal = Journal(path)
            journal.write({"phase": "PULLING"})
            self.assertEqual({"phase": "PULLING"}, journal.read())
            self.assertEqual(0o600, path.stat().st_mode & 0o777)
            journal.clear()
            self.assertIsNone(journal.read())

    def test_latest_release_api_rejects_prerelease(self):
        with tempfile.TemporaryDirectory() as directory:
            updater = Updater(None, Journal(Path(directory) / "state.json"), "secret",
                              opener=lambda *_args, **_kwargs: Response({"tag_name": "v2.5.0-rc.1", "prerelease": True}))
            self.assertIsNone(updater.latest_release())

    def test_latest_release_api_requires_stable_version_tag(self):
        with tempfile.TemporaryDirectory() as directory:
            updater = Updater(None, Journal(Path(directory) / "state.json"), "secret",
                              opener=lambda *_args, **_kwargs: Response({"tag_name": "latest", "draft": False, "prerelease": False}))
            with self.assertRaises(UpdateError):
                updater.latest_release()

    def test_drain_retries_when_service_is_not_ready_yet(self):
        class FakeUpdater(Updater):
            def __init__(self):
                super().__init__(None, None, "secret", sleep=lambda _: None)
                self.calls = 0

            def _api(self, service, path, method="GET"):
                self.calls += 1
                if path == "/update/status":
                    return {"phase": "RUNNING" if self.calls < 4 else "READY"}
                return None

        updater = FakeUpdater()
        self.assertTrue(updater._drain([Service("koemoji", "all", DEFAULT_IMAGE, 8080)]))
        self.assertGreaterEqual(updater.calls, 4)

    def test_update_control_authentication_failure_is_fatal(self):
        def reject(*_args, **_kwargs):
            raise urllib.error.HTTPError("http://koemoji/update/status", 401, "unauthorized", {}, None)

        updater = Updater(None, Journal(Path(tempfile.gettempdir()) / "unused-417.json"), "secret", opener=reject)
        with self.assertRaisesRegex(UpdateError, "HTTP 401"):
            updater._api(Service("koemoji", "all", DEFAULT_IMAGE, 8080), "/update/status")

    def test_drain_failure_prevents_update(self):
        class FailedDrainUpdater(Updater):
            def __init__(self):
                super().__init__(None, None, "secret", sleep=lambda _: None)
                self.cancelled = False

            def _api(self, service, path, method="GET"):
                if path == "/update/status":
                    return {"phase": "FAILED"}
                self.cancelled = path == "/update/cancel"
                return None

        updater = FailedDrainUpdater()
        self.assertFalse(updater._drain([Service("koemoji", "all", DEFAULT_IMAGE, 8080)]))
        self.assertTrue(updater.cancelled)

    def test_resume_pulls_before_drain_and_rolls_back_after_unhealthy_target(self):
        events = []

        class FakeCompose:
            def pull(self, services):
                events.append("pull")

            def image_metadata(self, image):
                return {"image_id": "target-id", "version": "2.0.0", "repo_digests": ["repo@sha256:target"]}

            def kill(self, services):
                events.append("kill")

            def up(self, services):
                events.append("up")

            def tag(self, image_id, image_ref):
                events.append("rollback-tag")

        with tempfile.TemporaryDirectory() as directory:
            updater = Updater(FakeCompose(), Journal(Path(directory) / "transaction.json"), "secret")
            updater._drain = lambda services: events.append("drain") or True
            health = iter((False, True))
            updater._healthy = lambda *args, **kwargs: next(health)
            transaction = {
                "id": "test",
                "release": "2.0.0",
                "image": DEFAULT_IMAGE,
                "services": [Service("koemoji", "all", DEFAULT_IMAGE, 8080).__dict__],
                "previous": {"koemoji": {"image_id": "prior-id", "version": "1.0.0"}},
                "phase": "PULLING",
            }

            self.assertTrue(updater.resume(transaction))
            self.assertLess(events.index("pull"), events.index("drain"))
            self.assertLess(events.index("drain"), events.index("kill"))
            self.assertLess(events.index("rollback-tag"), len(events) - 1)
            self.assertEqual(2, events.count("kill"))
            self.assertEqual(2, events.count("up"))


if __name__ == "__main__":
    unittest.main()
