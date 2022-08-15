import puppeteer, {
  BrowserConnectOptions,
  BrowserLaunchArgumentOptions,
  LaunchOptions,
  Product
} from "puppeteer-core";

async function main() {
  const puppeteerOptions: LaunchOptions &
    BrowserLaunchArgumentOptions &
    BrowserConnectOptions & {
      product?: Product;
      extraPrefsFirefox?: Record<string, unknown>;
    } = {
    headless: process.env["RECOGNIZER_HEADLESS"] !== "false",
    slowMo: 100,
    executablePath: "/usr/bin/chromium-browser",
    args: [
      "--no-sandbox",
      "--disable-setuid-sandbox",
      "--disable-dev-shm-usage",
      "--disable-accelerated-2d-canvas",
      "--no-zygote",
      "--disable-gpu",
      "--ignore-certificate-errors",
      "--allow-running-insecure-content",
      "--window-size=300,300",
    ],
    ignoreDefaultArgs: ["--mute-audio"],
  };
  const roomId = process.env["CHATWATCHER_ROOM_ID"];
  const url = "https://web/?roomId=" + roomId;

  const browser = await puppeteer.launch(puppeteerOptions);
  const context = browser.defaultBrowserContext();
  context.clearPermissionOverrides();
  context.overridePermissions(url, ["microphone"]);
  const page = await context.newPage();

  await page.goto(url, {
    waitUntil: "networkidle2",
  });
  await page.click("body");

  process
    .on("SIGINT", () => {
      browser.close();
    })
    .on("SIGTERM", () => {
      browser.close();
    })
    .on("SIGQUIT", () => {
      browser.close();
    });
}

(async () => {
  await main();
})();
