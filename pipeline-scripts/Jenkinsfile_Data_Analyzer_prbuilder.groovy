#!groovy


/** Specifying node on which current build would run */
node("NODE_LABEL") 
{
def mavenHome = tool 'maven'
def currentDir
def stageName
def commitHash 
def currentModules
def gitCommit
String buildNum = currentBuild.number.toString()
	
		stage('Git clone and setup')
		{
			stageName = "Git clone and setup"
			checkout scm
			def moduleProp = readProperties file: 'pipeline-scripts/properties/modules.properties'
			currentDir = pwd()
			MiscUtils = load("${currentDir}/pipeline-scripts/utils/MiscUtils.groovy")
			println("Reading modules.properties : $moduleProp")
			// Get the commit hash of PR branch 
			def branchCommit = sh( script: "git rev-parse refs/remotes/${sha1}^{commit}", returnStdout: true )
			
			// Get the commit hash of Master branch
			def masterCommit = sh( script: "git rev-parse origin/${ghprbTargetBranch}^{commit}", returnStdout: true )
			
			commitHash =  sh( script: "git rev-parse origin/${env.GIT_BRANCH}",returnStdout: true, )
			commitHash = commitHash.replaceAll("[\n\r]", "")
			branchCommit = branchCommit.replaceAll("[\n\r]", "")
			masterCommit = masterCommit.replaceAll("[\n\r]", "")
			println("branchCommit : $branchCommit")
			println("masterCommit : $masterCommit")
			def changeSet = MiscUtils.getChangeSet(branchCommit,masterCommit)
			def changedModules = MiscUtils.getModifiedModules(changeSet)
			def serviceModules = moduleProp['CJP_MODULES']
			def serviceModulesList = serviceModules.split(',')
			currentModules = MiscUtils.validateModules(changedModules,serviceModulesList)
			println("Service modules changed : $currentModules")			
			MiscUtils.setDisplayName(buildNum, currentModules)
		}
		stage('build & UT')
		{       
			for(module in currentModules)
			{
				def moduleProp = readProperties file: 'pipeline-scripts/properties/modules.properties'
				def packagePath = moduleProp['CJP_PACKAGEPATH']
				println("packagePath : $packagePath")
				packagePathMap = MiscUtils.stringToMap(packagePath)
				println("packagePathMap : $packagePathMap")
				def packageBuildPath = MiscUtils.getBuildPath(packagePathMap,module)
				//def command = MiscUtils.getBuildCommand(buildCommandMap,module)
				dir(packageBuildPath)
				{
					sh "'${mavenHome}/bin/mvn' clean package"
				}
			}
		}
		stage('sonarAnalysis')
		{       
			for(module in currentModules)
			{
				def moduleProp = readProperties file: 'pipeline-scripts/properties/modules.properties'
				def packagePath = moduleProp['CJP_PACKAGEPATH']
				//println("packagePath : $packagePath")
				packagePathMap = MiscUtils.stringToMap(packagePath)
				//println("packagePathMap : $packagePathMap")
				def packageBuildPath = MiscUtils.getBuildPath(packagePathMap,module)
				//def command = MiscUtils.getBuildCommand(buildCommandMap,module)
				dir(packageBuildPath)
				{
				withSonarQubeEnv('SonarDemo') {
				sh "'${mavenHome}/bin/mvn' sonar:sonar"
  					//-Dsonar.host.url=http://35.200.203.119:9000 \
  					//-Dsonar.login=bc7ed6c23eabd5e5001bcc733194bf9925c85efc"
			/*
			timeout(time: 1, unit: 'HOURS')
			{
				// Wait for SonarQube analysis to be completed and return quality gate status
				def quality = waitForQualityGate()
				if(quality.status != 'OK')
				{
					println("Quality Gate checks failed")
					throw new Exception("Quality Gate checks failed")
				}
				printlin("Quality Gate Checks passed ")
				}*/
				}
			}
		}
	}
			stage('Publish to Artifactory') 
			{
					println("Entering stage Publish to Artifactory")
					currentDir = pwd()
					CjpArtifactoryUtils = load("${currentDir}/pipeline-scripts/utils/CjpArtifactoryUtils.groovy")
					CjpConstants = load("${currentDir}/pipeline-scripts/utils/CjpConstants.groovy")
					MiscUtils = load("${currentDir}/pipeline-scripts/utils/MiscUtils.groovy")
					moduleProp = readProperties file: 'pipeline-scripts/properties/modules.properties'				
					commitHash =  sh( script: "git rev-parse origin/${env.GIT_BRANCH}",returnStdout: true, )
					gitCommit = commitHash.substring(0,7)
					stageName = "Publish to artifactory"
					def packageNames = moduleProp['PACKAGE_NAME']
					packageMap = MiscUtils.stringToMap(packageNames)
					tarPath = moduleProp['TAR_PATH']
					def tarPathMap = MiscUtils.stringToMap(tarPath)				
					for(module in currentModules) 
					{
						def packageName = MiscUtils.getValueFromMap(packageMap,module)
						def moduleTarPath = MiscUtils.getTarPath(tarPathMap,module)											
						dir(moduleTarPath)
						{
							sh"""
							#!/bin/bash
							tar cvf "${packageName}-${gitCommit}-b${buildNum}.tar" "/home/rameshrangaswamy1/workspace/PR_PHASE_1/cisco/target
/"
							"""
							CjpArtifactoryUtils.publishCcOneAppPackageMaster(CjpConstants.ARTIFACTORY_REPO, packageName, buildNum)
						}
					}
			}
}
