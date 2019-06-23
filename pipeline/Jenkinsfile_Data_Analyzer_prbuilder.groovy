#!groovy

//analyzer/pipeline-scripts/Jenkinsfile_Data_Analyzer_prbuilder.groovy

/**
 * This script is triggered for PR Builder
 * It is invoked by specific comment issued by user on PRs
 * Comments for this script is "DATA_VALIDATE"
 * Identifies the changes in the PR and performs validations for the same
*/



//@Library("ccc-pipeline-utils") _


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


//logger methods

//import Logger

/**
 * Centralized logging
 */


//import Constants

class Constants {

    static final String GREEN_APP_PREFIX = 'green-'
    static final String MERGE_COMMAND = 'MERGE'
    static final String TRIALTEST_COMMAND = 'TEST'
    static final String TRIALUPGRADE_COMMAND = 'TRIALUPGRADE'
    static final String SCHEMA_UPGRADE_COMMAND = 'UPGRADE'
    static final String MAC_MERGE_COMMAND = 'MAC_MERGE'
	
	static final String GITHUB_STATUS_URL = 'https://github.com/api/v3/repos'
	}


/** Specifying node on which current build would run */	

node(NODE_LABEL) 
{


	def mavenHome = tool 'maven'

	final String buildNum = currentBuild.number.toString()

	def currentModules

	def stageName

	def commitHash

	def MiscUtils

	def packagePathMap
	
	def Logger
	
	def GitUtils

		

	/** Stage to clone repo from git and setup environment for build */

	stage("Git clone and setup")

	{

		try 

		{
			println("HI")
			checkout scm
			Logger = load("${currentDir}/pipeline/utils/Logger.groovy")
			println("Reading modules.properties : $Logger")
			GitUtils = load("${currentDir}/pipeline/utils/GitUtils.groovy")
			
			Logger.debug("Entering PR Builder")

			Logger.info("Build trigger by $ghprbTriggerAuthor using comment $ghprbCommentBody")

			Logger.info("Entering Git Clone and setup stage")

			stageName = "Git clone and Setup"

			moduleProp = readProperties file: 'pipeline/properties/modules.properties'

			currentDir = pwd()

			MiscUtils = load("${currentDir}/pipeline/utils/MiscUtils.groovy")

			Logger.info("Reading modules.properties : $moduleProp")

			

			// Get the commit hash of PR branch 

			def branchCommit = sh( script: "git rev-parse refs/remotes/${sha1}^{commit}", returnStdout: true )

			

			// Get the commit hash of Master branch

			def masterCommit = sh( script: "git rev-parse origin/${ghprbTargetBranch}^{commit}", returnStdout: true )

			

			commitHash =  sh( script: "git rev-parse origin/${env.GIT_BRANCH}",returnStdout: true, )

			commitHash = commitHash.replaceAll("[\n\r]", "")

			branchCommit = branchCommit.replaceAll("[\n\r]", "")

			masterCommit = masterCommit.replaceAll("[\n\r]", "")

			

			def changeSet = MiscUtils.getChangeSet(branchCommit,masterCommit)

			def changedModules = MiscUtils.getModifiedModules(changeSet)

			def serviceModules = moduleProp['DATA_MODULES']

			def serviceModulesList = serviceModules.split(',')

			currentModules = MiscUtils.validateModules(changedModules,serviceModulesList)

			Logger.info("Service modules changed : $currentModules")

			MiscUtils.setDisplayName(buildNum, currentModules)

			GitUtils.updatePrStatus(stageName,"success",commitHash)			

		}

		catch(Exception exception)

		{

			currentBuild.result = "FAILURE"

			Logger.error("Git Clone and setup failed : $exception")

			GitUtils.updatePrStatus(stageName,"failure",commitHash)

			throw exception

		}

		finally

		{

			Logger.info("Exiting Git Clone and setup stage")

		}

		

	}

	

	/**
	* Setting up Maven Environment for build and UT stage
	* Stage to run UT's for changed modules
	*/

	

	withEnv([

                'MAVEN_HOME=' + mavenHome,

				'ANT_HOME=' + antHome,

                "PATH=${mavenHome}/bin:${antHome}/bin:${env.PATH}"

		])  

		{

			stage ("Build and UTs")

			{

				try

				{

					Logger.info("Entering Build and UT's stage")

					stageName = "Build and UTs"

					def buildCommand = moduleProp['DATA_ANT_MODULES']

					def buildCommandMap = MiscUtils.stringToMap(buildCommand)

					def packagePath = moduleProp['DATA_PACKAGEPATH']

					packagePathMap = MiscUtils.stringToMap(packagePath)

					for(module in currentModules)

					{

						def packageBuildPath = MiscUtils.getBuildPath(packagePathMap,module)

						def command = MiscUtils.getBuildCommand(buildCommandMap,module)

						dir(packageBuildPath)

						{

							Logger.info("Running UTs for $module")

							sh "$command"

						}

					}

					GitUtils.updatePrStatus(stageName,"success",commitHash)

				}

				catch(Exception exception) 

				{

					currentBuild.result = "FAILURE"

					Logger.error("Build and UT's failed : $exception")

					GitUtils.updatePrStatus(stageName,"failure",commitHash)

					throw exception

				}

				finally

				{

					Logger.info("Exiting Build and UTs stage")

				}

			} 

		}

		

	/** Stage to invoke Static Code Analyzer SonarQube */	

	

	withEnv([

                'MAVEN_HOME=' + mavenHome,

				'ANT_HOME=' + antHome,

                "PATH=${mavenHome}/bin:${antHome}/bin:${env.PATH}"

		]) 

	

	{	

		stage('Static Analysis') 

		{

			try

			{

				Logger.info("Entering Static Analysis stage")

				stageName = "Static Analysis"

				def sonarBranchName = MiscUtils.getSonarBranchName(ghprbSourceBranch)

				for (module in currentModules)

					{   

						def packageBuildPath = MiscUtils.getBuildPath(packagePathMap,module)

						dir(packageBuildPath)

						{

							readMavenPom file: 'pom.xml'

							artifactId = readMavenPom().getArtifactId()

							groupId = readMavenPom().getGroupId()

																			

							def metricKeys = moduleProp['METRIC_KEYS']

							def response								

							withCredentials([ usernamePassword(credentialsId: "Gideal", usernameVariable: 'USER', passwordVariable: 'PASS')])

							{

								

								def auth_key = "${USER}:${PASS}"

								def auth_encoded = auth_key.bytes.encodeBase64().toString()

								response = httpRequest consoleLogResponseBody: true,

									customHeaders: [[name: 'Authorization', value: "Basic ${auth_encoded}"]],

									httpMode: 'GET',

									url:"${Constants.SONARQUBE_API_URL}measures/component?componentKey=${groupId}:${artifactId}&metricKeys=$metricKeys"

							}

							def preScanJson = MiscUtils.getSonarMetrics(response.content)

							preScanJson = MiscUtils.stringToMap(preScanJson)

							Logger.info("preScanJson : $preScanJson")

							

							withSonarQubeEnv('CJPSonar') 

							{

								// requires SonarQube Scanner for Maven 3.2+

								sh "${mavenHome}/bin/mvn -Dsonar.branch.name=$sonarBranchName sonar:sonar"

							}

							Logger.info("Waiting for SonarQube Quality evaluation response")

							timeout(time: 1, unit: 'HOURS')

							{

								// Wait for SonarQube analysis to be completed and return quality gate status

								def quality = waitForQualityGate()

								if(quality.status != 'OK')

								{

									Logger.error("Quality gate check failed")

									throw new Exception("Quality Gate check failed")

								}

								Logger.info("Quality Gate check passed")

							}

							

							

							withCredentials([ usernamePassword(credentialsId: "Gideal", usernameVariable: 'USER', passwordVariable: 'PASS')])

							{

								

								def auth_key = "${USER}:${PASS}"

								def auth_encoded = auth_key.bytes.encodeBase64().toString()

								response = httpRequest consoleLogResponseBody: true,

									customHeaders: [[name: 'Authorization', value: "Basic ${auth_encoded}"]],

									httpMode: 'GET',

									url:"${Constants.SONARQUBE_API_URL}measures/component?branch=${sonarBranchName}&componentKey=${groupId}:${artifactId}&metricKeys=$metricKeys"

							}

							def postScanJson = MiscUtils.getSonarMetrics(response.content)

							postScanJson = MiscUtils.stringToMap(postScanJson)

							Logger.info("postScanJson : "+postScanJson)

							def metricList = metricKeys.split(',')

							for(metrics in metricList)

							{

								Logger.info("Sonar delta evaluation for following metric : "+metrics)

								preScanValue = MiscUtils.getValueFromMap(preScanJson,metrics)

								postScanValue = MiscUtils.getValueFromMap(postScanJson,metrics)

								

								sonarDelta = MiscUtils.sonarDelta(metrics,preScanValue, postScanValue)

								if(!sonarDelta)

								{

									Logger.info("Pre Scan Value :" +preScanValue)

									Logger.info("Post Scan Value :" +postScanValue)

									Logger.info("Sonar Violation has increased. Aborting Build")

									throw new Exception("Sonar Violation has increased")

								}	

							}

										

						}

					}

									

					

					GitUtils.updatePrStatus(stageName,"success",commitHash)

			}

		

			catch(Exception exception) 

			{

				currentBuild.result = "FAILURE"

				Logger.error("Static analysis failed : $exception")

				GitUtils.updatePrStatus(stageName,"failure",commitHash)

				throw exception

			}

			finally

			{

				Logger.info("Exting Static Analysis stage")

			}

		}

	}

	Logger.info("Exiting PR Builder")

}
