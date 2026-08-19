// ============================================================================
// Leaftaps — Jenkins Declarative Pipeline (Docker + Minikube + K8s Job)
// ============================================================================

pipeline {
    agent any

    parameters {
        choice(name: 'BROWSER', choices: ['chrome', 'firefox', 'edge'], description: 'Browser to use for UI test execution')
        choice(name: 'ENVIRONMENT', choices: ['dev', 'qa', 'prod'], description: 'Target environment')
        booleanParam(name: 'HEADLESS', defaultValue: true, description: 'Run browser in headless mode (recommended for CI)')
        string(name: 'SUITE_FILE', defaultValue: 'src/test/resources/suites/regression.xml', description: 'TestNG suite XML file to execute')
        string(name: 'AUTOFRAMEX_REPO', defaultValue: 'https://github.com/Rajeshluffy/autoFrameX.git', description: 'Git URL of the autoFrameX framework repository')
        string(name: 'AUTOFRAMEX_BRANCH', defaultValue: 'framework-3.1', description: 'Branch or tag to checkout for autoFrameX')
    }

    options {
        timestamps()
        timeout(time: 45, unit: 'MINUTES') // Increased pipeline timeout
        disableConcurrentBuilds()
        buildDiscarder(logRotator(numToKeepStr: '20', artifactNumToKeepStr: '10'))
    }

    environment {
        KUBECTL = 'docker exec minikube /var/lib/minikube/binaries/v1.35.1/kubectl --kubeconfig=/etc/kubernetes/admin.conf'
    }

    stages {
        stage('Checkout Leaftaps') {
            steps {
                dir('Leaftaps') { checkout scm }
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
                    docker cp Leaftaps/k8s/namespace.yaml minikube:/namespace.yaml
                    ${KUBECTL} apply -f /namespace.yaml

                    docker exec minikube rm -rf /leaftaps-data
                    docker exec minikube mkdir -p /leaftaps-data
                    
                    for f in Leaftaps/data/data/*.xlsx; do
                        docker cp "$f" minikube:/leaftaps-data/
                    done

                    ${KUBECTL} delete secret leaftaps-data -n leaftaps --ignore-not-found=true
                    ${KUBECTL} create secret generic leaftaps-data -n leaftaps --from-file=/leaftaps-data
                '''
            }
        }

        // Reverted from parallel groups back to a single sequential Job — this
        // machine has ~5.85GB total RAM, and two simultaneous headless-Chrome +
        // JVM pods starved minikube's control plane badly enough to crash the
        // API server mid-run (see build history). Not worth the instability
        // here; runs the full regression.xml suite in one pod instead.
        stage('Deploy to Kubernetes') {
            steps {
                sh '''
                    ${KUBECTL} delete job leaftaps-test-job -n leaftaps --ignore-not-found=true
                    sed -i "s/leaftaps-tests:latest/leaftaps-tests:${BUILD_ID}/g" Leaftaps/k8s/test-job.yaml
                    sed -i "s/value: \\"chrome\\"/value: \\"${BROWSER}\\"/;s/value: \\"qa\\"/value: \\"${ENVIRONMENT}\\"/;s/value: \\"true\\"/value: \\"${HEADLESS}\\"/;s#value: \\"src/test/resources/suites/regression.xml\\"#value: \\"${SUITE_FILE}\\"#" Leaftaps/k8s/test-job.yaml
                    docker cp Leaftaps/k8s/test-job.yaml minikube:/test-job.yaml
                    ${KUBECTL} apply -f /test-job.yaml
                '''
            }
        }

        stage('Collect Test Results') {
            steps {
                sh '''
                    # Poll instead of a blind 'kubectl wait --for=condition=complete': that
                    # condition never fires for a FAILED job, so it would burn the entire
                    # timeout doing nothing even when the pod died in the first few seconds.
                    # Exit the moment either Complete or Failed shows up (max ~10 min).
                    for i in $(seq 1 60); do
                        STATUS=$(${KUBECTL} get job/leaftaps-test-job -n leaftaps -o jsonpath='{.status.conditions[0].type}' 2>/dev/null)
                        if [ "$STATUS" = "Complete" ] || [ "$STATUS" = "Failed" ]; then
                            echo "Job reached status: $STATUS after ${i}0s"
                            break
                        fi
                        sleep 10
                    done

                    mkdir -p Leaftaps/target
                    docker exec minikube tar -c -C /tmp surefire-reports | tar -x -C Leaftaps/target
                '''
            }
        }
    }

    post {
        always {
            // CI DEBUGGING: Dump logs to Jenkins console before cleaning up
            echo "======================================================="
            echo "FETCHING KUBERNETES POD LOGS FOR DEBUGGING..."
            echo "======================================================="
            sh '''
                ${KUBECTL} logs -l job-name=leaftaps-test-job -n leaftaps --tail=200 || echo "Could not fetch logs"
            '''
            echo "======================================================="

            junit allowEmptyResults: true, testResults: 'Leaftaps/target/surefire-reports/*.xml'
            archiveArtifacts artifacts: 'Leaftaps/target/surefire-reports/**', allowEmptyArchive: true

            sh '${KUBECTL} delete job leaftaps-test-job -n leaftaps --ignore-not-found=true'
        }
        success { echo 'Pipeline complete — Leaftaps suite passed.' }
        failure { echo 'Pipeline failed — check Console Output and archived surefire-reports.' }
    }
}