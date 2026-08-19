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
                            
                            docker cp Leaftaps/k8s/test-job-g1.yaml minikube:/test-job-g1.yaml
                            ${KUBECTL} apply -f /test-job-g1.yaml
                            
                            # UPDATED TIMEOUT: 1800 seconds (30 minutes)
                            ${KUBECTL} wait --for=condition=complete job/leaftaps-test-job-g1 -n leaftaps --timeout=1800s || true
                            
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
                            
                            docker cp Leaftaps/k8s/test-job-g2.yaml minikube:/test-job-g2.yaml
                            ${KUBECTL} apply -f /test-job-g2.yaml
                            
                            # UPDATED TIMEOUT: 1800 seconds (30 minutes)
                            ${KUBECTL} wait --for=condition=complete job/leaftaps-test-job-g2 -n leaftaps --timeout=1800s || true
                            
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
            // CI DEBUGGING: Dump logs to Jenkins console before cleaning up
            echo "======================================================="
            echo "FETCHING KUBERNETES POD LOGS FOR DEBUGGING..."
            echo "======================================================="
            sh '''
                echo "--- GROUP 1 LOGS ---"
                ${KUBECTL} logs -l job-name=leaftaps-test-job-g1 -n leaftaps --tail=200 || echo "Could not fetch G1 logs"
                
                echo "--- GROUP 2 LOGS ---"
                ${KUBECTL} logs -l job-name=leaftaps-test-job-g2 -n leaftaps --tail=200 || echo "Could not fetch G2 logs"
            '''
            echo "======================================================="

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