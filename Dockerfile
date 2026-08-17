# ─────────────────────────────────────────────────────────────────────────
# Build context NOTE: this Dockerfile COPYs from a sibling project directory
# (../autoFrameX) because Leaftaps depends on autoFrameX's
# autoframex-selenium + autoframex-database modules — unpublished Maven
# artifacts with no shared Nexus/Artifactory in this environment (same v1
# simplification as GPN/Dockerfile — see its header comment). Build from the
# workspace/ PARENT directory, not from Leaftaps/ itself:
#
#   cd "D:\E Drive\Engineering\testleaf\workspace"
#   docker build -f Leaftaps/Dockerfile -t leaftaps-tests .
#
# The real, longer-term fix is a shared internal Maven repository
# (Nexus/Artifactory/GitHub Packages) — this build-context workaround is a
# deliberate v1 simplification, not the end state.
# ─────────────────────────────────────────────────────────────────────────
FROM maven:3.9-eclipse-temurin-17

LABEL org.opencontainers.image.title="Leaftaps"
LABEL org.opencontainers.image.description="Leaftaps (OpenTaps CRM) Selenium/TestNG suite — built on autoFrameX"

# Install Chrome via official Google .deb (stable, Debian-compatible base image)
RUN apt-get update -qq && \
    apt-get install -y --no-install-recommends \
        wget \
        gnupg \
        ca-certificates \
        fonts-liberation \
        libappindicator3-1 \
        libasound2 \
        libatk-bridge2.0-0 \
        libatk1.0-0 \
        libcups2 \
        libdbus-1-3 \
        libgdk-pixbuf2.0-0 \
        libnspr4 \
        libnss3 \
        libx11-xcb1 \
        libxcomposite1 \
        libxdamage1 \
        libxrandr2 \
        xdg-utils && \
    wget -q -O /tmp/google-chrome.deb \
        https://dl.google.com/linux/direct/google-chrome-stable_current_amd64.deb && \
    apt-get install -y --no-install-recommends /tmp/google-chrome.deb && \
    rm /tmp/google-chrome.deb && \
    apt-get clean && \
    rm -rf /var/lib/apt/lists/*

RUN google-chrome --version

WORKDIR /app

# ── Layer 1: dependency cache — every pom, so go-offline sees the full graph ──
COPY autoFrameX/pom.xml autoFrameX/
COPY autoFrameX/autoframex-core/pom.xml autoFrameX/autoframex-core/
COPY autoFrameX/autoframex-selenium/pom.xml autoFrameX/autoframex-selenium/
COPY autoFrameX/autoframex-api/pom.xml autoFrameX/autoframex-api/
COPY autoFrameX/autoframex-database/pom.xml autoFrameX/autoframex-database/
COPY autoFrameX/autoframex-cucumber/pom.xml autoFrameX/autoframex-cucumber/
COPY autoFrameX/autoframex-performance/pom.xml autoFrameX/autoframex-performance/
COPY autoFrameX/autoframex-security/pom.xml autoFrameX/autoframex-security/
COPY autoFrameX/autoframex-testkit/pom.xml autoFrameX/autoframex-testkit/
COPY Leaftaps/pom.xml Leaftaps/
RUN mvn -f autoFrameX/pom.xml dependency:go-offline -q

# ── Layer 2: source — invalidated on any source change ──
COPY autoFrameX/autoframex-core/ autoFrameX/autoframex-core/
COPY autoFrameX/autoframex-selenium/ autoFrameX/autoframex-selenium/
COPY autoFrameX/autoframex-api/ autoFrameX/autoframex-api/
COPY autoFrameX/autoframex-database/ autoFrameX/autoframex-database/
COPY autoFrameX/autoframex-cucumber/ autoFrameX/autoframex-cucumber/
COPY autoFrameX/autoframex-performance/ autoFrameX/autoframex-performance/
COPY autoFrameX/autoframex-security/ autoFrameX/autoframex-security/
COPY autoFrameX/autoframex-testkit/ autoFrameX/autoframex-testkit/
COPY Leaftaps/src/ Leaftaps/src/
# Leaftaps/data/*.xlsx (Login, CreateLead, EditLead, DeleteLead, DuplicateLead)
# are Excel data-provider fixtures — deliberately NOT copied here (see
# Leaftaps/.gitignore, "#secrets"). Supply them at container runtime via a
# mounted volume instead:
#   -v "$(pwd)/data:/app/Leaftaps/data:ro"

# Install in dependency order: autoFrameX reactor -> Leaftaps.
RUN mvn -f autoFrameX/pom.xml clean install -DskipTests -Djacoco.skip=true -q && \
    mvn -f Leaftaps/pom.xml clean install -DskipTests -q

WORKDIR /app/Leaftaps

# Runtime defaults — all overridable via --env at docker run or in a Compose/K8s
# manifest. Honored generically by the framework (ProjectDirector /
# DriverPoolManager): TestNG parameter -> env var -> -D system property -> default.
ENV BROWSER=chrome
ENV HEADLESS=true
ENV ENVIRONMENT=qa
ENV SUITE_FILE=src/test/resources/suites/regression.xml

# Mount these volumes to retrieve test artifacts from the host after the container exits
VOLUME ["/app/Leaftaps/reports", "/app/Leaftaps/logs", "/app/Leaftaps/target/surefire-reports"]

# Shell-form ENTRYPOINT so ${ENV_VAR} values expand at container runtime
ENTRYPOINT mvn test \
    -DsuiteXmlFile=${SUITE_FILE} \
    -Dbrowser=${BROWSER} \
    -Denv=${ENVIRONMENT} \
    -Dheadless=${HEADLESS}
