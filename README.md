# LeafTaps CRM UI Test Automation

Selenium/TestNG UI regression suite for the LeafTaps CRM demo application (lead management), built with a **Page Object Model** design on top of [autoFrameX](https://github.com/Rajeshluffy/autoFrameX) — a shared, modular Selenium/REST test framework — and packaged for CI/CD with Docker, Jenkins, and Kubernetes.

## What it tests

End-to-end lead lifecycle in the LeafTaps CRM:

| Test case | Scenario |
|---|---|
| `TC001_VerifyLogin` | Valid login redirects to the CRM home page |
| `TC002_VerifyLogout` | Logout returns the user to the login page |
| `TC003_CreateLead` | Create a new lead and verify it's saved |
| `TC004_EditLead` | Find an existing lead and edit its details |
| `TC005_DeleteLead` | Delete a lead and confirm it no longer appears |
| `TC006_DuplicateLead` | Duplicate an existing lead and verify the copy |

Each test case is backed by a dedicated Page Object (`CreateLeadPage`, `EditLeadPage`, `FindLeadPage`, `MyLeadsPage`, etc.), keeping locators and page interactions out of the test logic. Test data is data-driven from Excel fixtures (`Login.xlsx`, `CreateLead.xlsx`, `EditLead.xlsx`, `DeleteLead.xlsx`, `DuplicateLead.xlsx`).

## Tech stack

| Layer | Tool |
|---|---|
| Language | Java 16 |
| UI automation | Selenium WebDriver (Chrome / Firefox / Edge) |
| Test runner | TestNG (suite file: `src/test/resources/suites/regression.xml`) |
| Design pattern | Page Object Model |
| Test data | Excel-driven fixtures |
| Database | JDBC via `autoframex-database` (`LeafTapsDatabasePage`) — for data-layer assertions alongside UI checks |
| Build | Maven |
| Shared framework | [autoFrameX](https://github.com/Rajeshluffy/autoFrameX) — `autoframex-selenium` + `autoframex-database` modules |
| CI/CD | Jenkins declarative pipeline |
| Runtime | Docker image deployed as a one-shot Kubernetes `Job` on Minikube |

## Architecture

```
src/test/java/com/leaftaps/pages/       Page Objects — one class per CRM screen
src/test/java/com/leaftaps/testcases/   TestNG test classes (TC001–TC006)
src/test/java/com/leaftaps/config/data/ Test data / config
src/test/resources/suites/              TestNG suite XML files
k8s/                                    Namespace + Job manifests for the test run
Dockerfile                              Builds autoFrameX + this project into one test image
Jenkinsfile                             Full CI/CD pipeline (see below)
```

`autoframex-selenium` supplies the base test lifecycle, driver management, config, and locator utilities; `autoframex-database` is pulled in separately since `LeafTapsDatabasePage` talks to the CRM's backing database directly — not every consumer of the framework needs a DB dependency, so it's kept as an opt-in module.

## CI/CD pipeline

The `Jenkinsfile` runs a parameterized, self-hosted pipeline (Docker + Minikube + Kubernetes `Job`, no external registry required):

1. **Checkout** this repo and a sibling checkout of `autoFrameX` (parameterized branch)
2. **Unpack test data** — the Excel fixtures are git-ignored and never baked into the image; they're supplied at build time from a Jenkins Secret file credential (a zip of the `.xlsx` files) and synced into a Kubernetes `Secret`
3. **Build** a Docker image containing the built autoFrameX reactor + this project
4. **Load** the image directly into a Minikube node (`docker save` → `docker load` inside the node)
5. **Deploy** the test image as a one-shot Kubernetes `Job`
6. **Collect results** — wait for the Job to complete, stream `surefire-reports` back out of the node, publish JUnit results

Build parameters: `BROWSER` (chrome/firefox/edge), `ENVIRONMENT` (dev/qa/prod), `HEADLESS` (default true), `SUITE_FILE`.

Required Jenkins credentials: a Secret file with ID `leaftaps-data-zip` containing the Excel fixtures.

## Running locally

```bash
# 1. Build and install the autoFrameX reactor once
git clone https://github.com/Rajeshluffy/autoFrameX.git
cd autoFrameX && mvn install -DskipTests -Djacoco.skip=true

# 2. Run this suite
cd ../Leaftaps
mvn test

# Or target a different suite file:
mvn test -DsuiteXmlFile=src/test/resources/suites/smoke.xml
```
#
