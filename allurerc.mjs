import { env } from "node:process";
import { defineConfig } from "allure";

export default defineConfig({
  plugins: {
    awesome: {
      options: {
        publish: true,
      },
    },
  },
  allureService: {
    accessToken: env.ALLURE_ACCESS_TOKEN,
  },
});
