// Jenkins pipeline for mini-stock-exchange.
// Job type: Multibranch Pipeline → every branch (develop, main, feature/*) with this file gets its own pipeline.
//
// Stages:
//   1. Checkout      - get the code for this branch
//   2. Build & Test  - compile both modules and run all 60 tests (engine first, then order-service)
//   3. Package       - keep the runnable order-service jar as a build artifact
//
// Next steps (later): Docker image build + push, deploy.

pipeline {
    agent any

    options {
        timestamps()                                    // time on every log line
        timeout(time: 20, unit: 'MINUTES')              // kill a stuck build
        buildDiscarder(logRotator(numToKeepStr: '10'))  // keep only the last 10 builds
        disableConcurrentBuilds()                       // one build per branch at a time
    }

    triggers {
        // Jenkins on a laptop can't receive GitHub webhooks, so check GitHub for new commits every 5 minutes.
        pollSCM('H/5 * * * *')
    }

    stages {
        stage('Checkout') {
            steps {
                checkout scm
                sh 'git log -1 --oneline'
            }
        }

        stage('Build & Test') {
            steps {
                sh 'chmod +x mvnw'                      // Windows commits lose the executable bit
                sh './mvnw -B -ntp clean verify'        // -B batch mode, -ntp no download progress spam
            }
            post {
                always {
                    // Test results graph + per-test pass/fail in the Jenkins UI
                    junit allowEmptyResults: true, testResults: '**/target/surefire-reports/*.xml'
                }
            }
        }

        stage('Package') {
            steps {
                archiveArtifacts artifacts: 'order-service/target/order-service-*.jar', fingerprint: true
            }
        }
    }

    post {
        success { echo "✅ ${env.BRANCH_NAME} #${env.BUILD_NUMBER}: build and all tests passed" }
        failure { echo "❌ ${env.BRANCH_NAME} #${env.BUILD_NUMBER}: failed, check the stage logs and test report" }
    }
}
