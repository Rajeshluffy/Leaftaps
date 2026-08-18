// ============================================================================
// Leaftaps — Jenkins Declarative Pipeline (Docker + Minikube + K8s Job)
//
// Flow:
//   1. Checkout Leaftaps and autoFrameX (sibling checkouts — Leaftaps depends
//      on autoFrameX's autoframex-selenium/autoframex-database modules,
//      which only exist via local Maven install; see Dockerfile header)
//   2. Unpack the Excel data-provider fixtures (Login/CreateLead/EditLead/
//      DeleteLead/DuplicateLead.xlsx) from a Jenkins Secret file credential —
//      these are git-ignored (see Leaftaps/.gitignore, "#secrets") and never
//      baked into the image
//   3. Build ONE Docker image (installs the autoFrameX reactor, then
//      Leaftaps, inside the image — see Dockerfile)
//   4. Load the image into the Minikube node (no external registry)
//   5. Sync the Excel fixtures into a K8s Secret
//   6. Deploy TWO k8s Jobs in parallel from the same image, each pinned to
//      a different TestNG suite file (group1.xml / group2.xml) via the
//      SUITE_FILE env var and writing reports to its own hostPath dir
//      (/tmp/surefire-reports-g1, -g2) so they can't clobber each other
//   7. Wait for both, pull both sets of surefire-reports back out, and
//      publish a single merged JUnit result
//
// First-time setup checklist
// ──────────────────────────
// Jenkins → Manage Jenkins → Credentials → (global) → Add Credentials
//   [ ] Secret file, ID "leaftaps-data-zip" — a .zip containing
//       Login.xlsx, CreateLead.xlsx, EditLead.xlsx, DeleteLead.xlsx,
//       DuplicateLead.xlsx at its root (matching Leaftaps/data/*.xlsx today)
//
// Jenkins agent requirements
//   [ ] Docker CLI + a running Minikube node named "minikube" reachable via
//       `docker exec minikube ...` (same setup as the GPN/serivcenow
//       pipelines already running in this environment)
//   [ ] `unzip` available on the agent
//
// Jenkins → New Item → Pipeline
//   [ ] Pipeline Definition : Pipeline script from SCM
//   [ ] SCM                 : Git → URL of the Leaftaps repository
//   [ ] Script Path         : Jenkinsfile   (this file)
// ============================================================================

pipeline {

    agent any

    parameters {
        choice(
            name: 'BROWSER',
            choices: ['chrome', 'firefox', 'edge'],
            description: 'Browser to use for UI test execution'
        )
        choice(
            name: 'ENVIRONMENT',
            choices: ['dev', 'qa', 'prod'],
            description: 'Target environment'
        )
        booleanParam(
            name: 'HEADLESS',
            defaultValue: true,
            description: 'Run browser in headless mode (recommended for CI)'
        )
        string(
            name:         'SUITE_FILE',
            defaultValue: 'src/test/resources/suites/regression.xml',
            description:  'TestNG suite XML file to execute (relative to the Leaftaps/ directory)'
        )
        string(
            name:         'AUTOFRAMEX_REPO',
            defaultValue: 'https://github.com/Rajeshluffy/autoFrameX.git',
            description:  'Git URL of the autoFrameX framework repository'
        )
        string(
            name:         'AUTOFRAMEX_BRANCH',
            defaultValue: 'framework-3.1',
            description:  'Branch or tag to checkout for autoFrameX'
        )
    }

    options {
        timestamps()
        timeout(time: 30, unit: 'MINUTES')
        disableConcurrentBuilds()
        buildDiscarder(logRotator(numToKeepStr: '20', artifactNumToKeepStr: '10'))
    }

    environment {
        KUBECTL = 'docker exec minikube /var/lib/minikube/binaries/v1.35.1/kubectl --kubeconfig=/etc/kubernetes/admin.conf'
    }

    stages {

        stage('Checkout Leaftaps') {
            steps {
                dir('Leaftaps') {
                    checkout scm
                }
            }
        }

        stage('Checkout autoFrameX') {
            steps {
                dir('autoFrameX') {
                    git url: params.AUTOFRAMEX_REPO, branch: params.AUTOFRAMEX_BRANCH
                }
            }
        }

        stage('Inject Test Data') {
            steps {
                withCredentials([file(credentialsId: 'leaftaps-data-zip', variable: 'DATA_ZIP')]) {
                    sh '''
                        mkdir -p Leaftaps/data
                        unzip -o "$DATA_ZIP" -d Leaftaps/data
                    '''
                }
            }
        }

        stage('Build Docker Image') {
            steps {
                // Build context = workspace root, which now contains both
                // Leaftaps/ and autoFrameX/ as siblings — see Dockerfile
                // header comment for why.
                sh 'docker build --platform linux/amd64 --provenance=false -f Leaftaps/Dockerfile -t leaftaps-tests:${BUILD_ID} .'
            }
        }

        stage('Load Image into Minikube') {
            steps {
                sh '''
                    docker save -o leaftaps-tests.tar leaftaps-tests:${BUILD_ID}
                    docker cp leaftaps-tests.tar minikube:/leaftaps-tests.tar
                    docker exec minikube docker load -i /leaftaps-tests.tar
                    rm leaftaps-tests.tar
                    docker exec minikube rm /leaftaps-tests.tar
                '''
            }
        }

        stage('Sync Test Data Secret') {
            steps {
                sh '''
            # 1. Copy namespace YAML to the root directory (avoids tmpfs issues)
            docker cp Leaftaps/k8s/namespace.yaml minikube:/namespace.yaml
            
            # 2. Apply it directly from the root path
            ${KUBECTL} apply -f /namespace.yaml

            # 3. Use a safe root path for the Excel files as well
            docker exec minikube rm -rf /leaftaps-data
            docker exec minikube mkdir -p /leaftaps-data
            
            # 4. Copy all Excel files into the new directory
            for f in Leaftaps/data/*.xlsx; do
                docker cp "$f" minikube:/leaftaps-data/
            done

            # 5. Recreate the Kubernetes secret using the new path
            ${KUBECTL} delete secret leaftaps-data -n leaftaps --ignore-not-found=true
            ${KUBECTL} create secret generic leaftaps-data -n leaftaps --from-file=/leaftaps-data
        '''
    
            }
        }

        // Both groups run from the SAME image built above (only the suite file
        // differs), so no second build/load is needed — just two Jobs deployed
        // and awaited side by side, each writing to its own hostPath directory
        // on the Minikube node so they can't clobber each other's reports.
        stage('Deploy & Run Parallel Groups') {
            parallel {
                stage('Group 1: Login/Logout/Create/Edit') {
                    steps {
                        sh '''
                            ${KUBECTL} delete job leaftaps-test-job-g1 -n leaftaps --ignore-not-found=true
                            cp Leaftaps/k8s/test-job.yaml Leaftaps/k8s/test-job-g1.yaml
                            sed -i "s/leaftaps-tests:latest/leaftaps-tests:${BUILD_ID}/g" Leaftaps/k8s/test-job-g1.yaml
                            sed -i "s/name: leaftaps-test-job/name: leaftaps-test-job-g1/" Leaftaps/k8s/test-job-g1.yaml
                            sed -i "s#/tmp/surefire-reports#/tmp/surefire-reports-g1#" Leaftaps/k8s/test-job-g1.yaml
                            sed -i "s/value: \\"chrome\\"/value: \\"${BROWSER}\\"/;s/value: \\"qa\\"/value: \\"${ENVIRONMENT}\\"/;s/value: \\"true\\"/value: \\"${HEADLESS}\\"/;s#value: \\"src/test/resources/suites/regression.xml\\"#value: \\"src/test/resources/suites/group1.xml\\"#" Leaftaps/k8s/test-job-g1.yaml
                            cat Leaftaps/k8s/test-job-g1.yaml | ${KUBECTL} apply -f -
                            ${KUBECTL} wait --for=condition=complete job/leaftaps-test-job-g1 -n leaftaps --timeout=600s || true
                            mkdir -p Leaftaps/target/group1
                            docker exec minikube tar -c -C /tmp surefire-reports-g1 | tar -x -C Leaftaps/target/group1 --strip-components=0
                        '''
                    }
                }
                stage('Group 2: Delete/Duplicate/Verify') {
                    steps {
                        sh '''
                            ${KUBECTL} delete job leaftaps-test-job-g2 -n leaftaps --ignore-not-found=true
                            cp Leaftaps/k8s/test-job.yaml Leaftaps/k8s/test-job-g2.yaml
                            sed -i "s/leaftaps-tests:latest/leaftaps-tests:${BUILD_ID}/g" Leaftaps/k8s/test-job-g2.yaml
                            sed -i "s/name: leaftaps-test-job/name: leaftaps-test-job-g2/" Leaftaps/k8s/test-job-g2.yaml
                            sed -i "s#/tmp/surefire-reports#/tmp/surefire-reports-g2#" Leaftaps/k8s/test-job-g2.yaml
                            sed -i "s/value: \\"chrome\\"/value: \\"${BROWSER}\\"/;s/value: \\"qa\\"/value: \\"${ENVIRONMENT}\\"/;s/value: \\"true\\"/value: \\"${HEADLESS}\\"/;s#value: \\"src/test/resources/suites/regression.xml\\"#value: \\"src/test/resources/suites/group2.xml\\"#" Leaftaps/k8s/test-job-g2.yaml
                            cat Leaftaps/k8s/test-job-g2.yaml | ${KUBECTL} apply -f -
                            ${KUBECTL} wait --for=condition=complete job/leaftaps-test-job-g2 -n leaftaps --timeout=600s || true
                            mkdir -p Leaftaps/target/group2
                            docker exec minikube tar -c -C /tmp surefire-reports-g2 | tar -x -C Leaftaps/target/group2 --strip-components=0
                        '''
                    }
                }
            }
        }

    }

    post {
        always {
            junit allowEmptyResults: true, testResults: 'Leaftaps/target/group*/surefire-reports*/*.xml'
            archiveArtifacts artifacts: 'Leaftaps/target/group*/**', allowEmptyArchive: true
            sh '''
                ${KUBECTL} delete job leaftaps-test-job-g1 -n leaftaps --ignore-not-found=true
                ${KUBECTL} delete job leaftaps-test-job-g2 -n leaftaps --ignore-not-found=true
            '''
        }
        success { echo 'Pipeline complete — Leaftaps suite passed.' }
        failure { echo 'Pipeline failed — check Console Output and archived surefire-reports.' }
    }

}
