// Jenkins pipeline for mini-stock-exchange.
// Job type: normal Pipeline ("Pipeline script from SCM") or Multibranch Pipeline, both work.
//
// Stages:
//   1. Checkout          - get the code for this branch
//   2. Build & Test      - compile both modules, run all 60 tests (dependencies come through Nexus)
//   3. Package           - keep the runnable order-service jar as a Jenkins artifact
//   4. Publish to Nexus  - develop/main only: upload the jars to Nexus (SNAPSHOT → maven-snapshots)
//
// Needs in Jenkins: a "Username with password" credential with id "nexus-creds" (Nexus user jenkins-ci).
// Jenkins and Nexus run as Docker containers on the same network "cicd", so Nexus is http://nexus:8081.

pipeline {
    agent any

    options {
        timestamps()
        timeout(time: 20, unit: 'MINUTES')
        buildDiscarder(logRotator(numToKeepStr: '10'))
        disableConcurrentBuilds()
    }

    triggers {
        // Jenkins on a laptop can't receive GitHub webhooks, so check for new commits every 5 minutes.
        pollSCM('H/5 * * * *')
    }

    environment {
        NEXUS_URL = 'http://nexus:8081'
        MVN       = './mvnw -B -ntp -s ci/settings.xml'
    }

    stages {
        stage('Checkout') {
            steps {
                checkout scm
                sh 'git log -1 --oneline'
                sh 'chmod +x mvnw'                      // Windows commits can lose the executable bit
            }
        }

        stage('Build & Test') {
            steps {
                withCredentials([usernamePassword(credentialsId: 'nexus-creds',
                        usernameVariable: 'NEXUS_USER', passwordVariable: 'NEXUS_PASS')]) {
                    sh "${MVN} clean verify"
                }
            }
            post {
                always {
                    junit allowEmptyResults: true, testResults: '**/target/surefire-reports/*.xml'
                }
            }
        }

        stage('Package') {
            steps {
                archiveArtifacts artifacts: 'order-service/target/order-service-*.jar', fingerprint: true
            }
        }

        stage('Publish to Nexus') {
            // Works for both job types:
            //   Multibranch Pipeline → BRANCH_NAME = "develop"
            //   normal Pipeline      → GIT_BRANCH  = "origin/develop"
            when { expression { (env.BRANCH_NAME ?: env.GIT_BRANCH ?: '') ==~ /(origin\/)?(develop|main)/ } }
            steps {
                withCredentials([usernamePassword(credentialsId: 'nexus-creds',
                        usernameVariable: 'NEXUS_USER', passwordVariable: 'NEXUS_PASS')]) {
                    // Tests already passed above, so skip them here.
                    // SNAPSHOT versions go to maven-snapshots, release versions to maven-releases.
                    sh """
                        ${MVN} deploy -DskipTests \
                          -DaltSnapshotDeploymentRepository=nexus-snapshots::${NEXUS_URL}/repository/maven-snapshots/ \
                          -DaltReleaseDeploymentRepository=nexus-releases::${NEXUS_URL}/repository/maven-releases/
                    """
                }
            }
        }
    }

    post {
        success { echo "✅ ${env.BRANCH_NAME ?: env.GIT_BRANCH} #${env.BUILD_NUMBER}: build, tests and publish passed" }
        failure { echo "❌ ${env.BRANCH_NAME ?: env.GIT_BRANCH} #${env.BUILD_NUMBER}: failed, check the stage logs and test report" }
    }
}
