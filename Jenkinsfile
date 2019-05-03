node {
  final scmVars = checkout(scm)
  //checkout scm
  env.PATH = "${tool 'Maven3'}/bin:${env.PATH}"

  stage ("Running Unit Tests") {
      try {
          echo "scmVars: ${scmVars}"
          echo "scmVars.GIT_COMMIT: ${scmVars.GIT_COMMIT}"
          echo "scmVars.GIT_BRANCH: ${scmVars.GIT_BRANCH}"

          //echo sh(returnStdout: true, script: 'env')
          sh 'cp service.example.envs service.envs'
          sh 'mvn clean test'
          //slackSend color: "good", message: "Build Succeeded: ${env.JOB_NAME} ${env.BUILD_NUMBER} \nGit Commit: ${scmVars.GIT_COMMIT} \nGit Branch: ${scmVars.GIT_BRANCH}"
      } catch (error) {
          //slackSend color: "danger", message: "Build Failed Running Unit Tests: ${env.JOB_NAME} ${env.BUILD_NUMBER} \nGit Commit: ${scmVars.GIT_COMMIT} \nGit Branch: ${scmVars.GIT_BRANCH}"
      } finally {
          junit '**/target/surefire-reports/*.xml'
      }
  }

  //    stage ('Building Docker Images') {
  //        try {
  //            sh 'docker-compose up -d --build'
  //        } catch (error) {
  //            slackSend color: "danger", message: "Build Failed Building Docker Images: ${env.JOB_NAME} ${env.BUILD_NUMBER}"
  //        }
  //    }
  //
  //    stage ('Wait for Docker Containers Init Prior Testing') {
  //        //waitUntil {
  //        //   sh 'wget --retry-connrefused --tries=120 --waitretry=1 -q localhost:8110/status/resources/v1/status -O /dev/null'
  //        //}
  //        try {
  //            echo "Waiting 20 seconds for Docker Containers initialize prior to testing"
  //            sleep 20 // seconds
  //        } catch (error) {
  //        }
  //    }
  //
  //    stage('Running Integration Tests') {
  //        try {
  //            sh 'mvn test -Parq-wildfly-remote'
  //            sh 'docker-compose down'
  //            slackSend color: "good", message: "Build Succeeded: ${env.JOB_NAME} ${env.BUILD_NUMBER}"
  //        } catch (error) {
  //            slackSend color: "danger", message: "Build Failed Running Integration Tests: ${env.JOB_NAME} ${env.BUILD_NUMBER}"
  //        } finally {
  //            junit '**/target/surefire-reports/*.xml'
  //        }
  //    }
}
