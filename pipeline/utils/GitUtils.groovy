#!groovy

import groovy.json.JsonSlurper
def checkoutCommitHash() {
    checkout scm
    if(!env.BUILD_COMMIT_HASH) {
        env.BUILD_COMMIT_HASH = getCommitHash()
    }
    sh "git checkout $BUILD_COMMIT_HASH"
}

def getCommitHash() {
    def gitCommit = sh(returnStdout: true, script: 'git rev-parse HEAD').trim()
    return gitCommit
}

/*
def mergePrToMaster(prNum, prBranch) {
    sshagent([GIT_AUTH]) {
        sh """ git config --global user.email "***" """
        sh """ git config --global user.name "****" """
        sh "git fetch origin"
        sh "git checkout master"
        sh """ git merge --no-ff -m "Merge pull request #$prNum from $prBranch" origin/$prBranch """
        sh "git push origin master"
    }
}*/

// To checkout desired repo and branch
def gitCheckout(String repo, String buildBranch, String targetDir = repo)
{
	checkout([$class: 'GitSCM',
	branches: [[name: buildBranch]],
	doGenerateSubmoduleConfigurations: false,
	extensions: [[$class: 'RelativeTargetDirectory', relativeTargetDir: targetDir]],
	submoduleCfg: [],
	userRemoteConfigs: [[credentialsId: 'ramesh-testpipelineadmin',
	refspec: '+refs/heads/*:refs/remotes/origin/*',
	url: 'git@github.com:' + repo + '.git']]
	])
}

/** Method to update PR Status in GIT */
def updatePrStatus(context, status, commitId=ghprbActualCommit) {
    def payload = """ {
        "state": "$status",
        "target_url": "${currentBuild.absoluteUrl}",
        "context": "$context"
    }"""
    withCredentials([string(credentialsId: 'ramesh-testpipelineadmin', variable: 'GITHUB_TOKEN')]) {
        def response = httpRequest consoleLogResponseBody: true,
                customHeaders: [[name: 'Authorization', value: "token ${GITHUB_TOKEN}"]],
                httpMode: 'POST', requestBody: payload,
url: "${Constants.GITHUB_STATUS_URL}/${ghprbGhRepository}/statuses/${commitId}"

        println("Build status update status: " + response.status + ", response: " + response.content)
    }
}