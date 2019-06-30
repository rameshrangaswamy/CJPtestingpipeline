#!groovy

//analyzer/pipeline-scripts/Jenkinsfile_Data_Analyzer_prbuilder.groovy

/**
 * This script is triggered for PR Builder
 * It is invoked by specific comment issued by user on PRs
 * Comments for this script is "DATA_VALIDATE"
 * Identifies the changes in the PR and performs validations for the same
*/


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
	
	def CjpConstants
	
			

	/** Stage to clone repo from git and setup environment for build */

	stage("Git clone and setup")

	{

		try 

		{
			checkout scm
			
			def currentDir
			
			currentDir = pwd()
			
			Logger = load("${currentDir}/pipeline/utils/Logger.groovy")
			
			GitUtils = load("${currentDir}/pipeline/utils/GitUtils.groovy")
			
			CjpConstants = load("${currentDir}/pipeline/utils/CjpConstants.groovy")
			
			Logger.info("Entering PR Builder")

			Logger.info("Build trigger by $ghprbTriggerAuthor using comment $ghprbCommentBody")

			Logger.info("Entering Git Clone and setup stage")

			stageName = "Git clone and Setup"

			moduleProp = readProperties file: 'pipeline/properties/modules.properties'

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

			def serviceModules = moduleProp['CJP_MODULES']

			def serviceModulesList = serviceModules.split(',')

			currentModules = MiscUtils.validateModules(changedModules,serviceModulesList)

			Logger.info("Service modules changed : $currentModules")

			MiscUtils.setDisplayName(buildNum, currentModules)
			
			Logger.info("$status + ${currentBuild.absoluteUrl} + $context")


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


}
