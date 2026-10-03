# Play Console policy answers

Answers for the *App content* section, matching what the code does in version 1.0.0.

## Data safety

Ren declares no `INTERNET` permission and bundles no analytics, ads or crash-reporting SDKs.

| Question | Answer |
|---|---|
| Does your app collect or share any of the required user data types? | **No** |
| Is all of the user data collected by your app encrypted in transit? | Not applicable (nothing is transmitted) |
| Do you provide a way for users to request that their data is deleted? | Not applicable (no data collected; uninstalling removes everything) |

Progress may be included in the user's own Android Auto Backup. Google's guidance treats data
handled by Android backup on the user's behalf as not collected by the developer.

## Other declarations

| Section | Answer |
|---|---|
| Privacy policy | Public URL of `PRIVACY.md` |
| Ads | No, the app does not contain ads |
| App access | All functionality is available without special access |
| Content rating (IARC) | Reference / education app; no violence, sexuality, language, controlled substances, gambling or user interaction. Expected rating: Everyone / PEGI 3 |
| Target audience | 13 and over (keeps the app outside the Families programme) |
| News app | No |
| Government app | No |
| Financial features | None |
| Health | No |
| Exact alarm / foreground service / restricted permissions | None used. The reminder runs on WorkManager. |
