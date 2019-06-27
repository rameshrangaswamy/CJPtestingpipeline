#!groovy

/** provide comment as TRIALTEST to trigger this job */

/** Specifying node on which current build would run */

node("NODE_LABEL") 

{

def mavenHome = tool 'maven'

def stageName

def commitHash 

def currentModules

def gitCommit

String buildNum = currentBuild.number.toString()

def server = Artifactory.server 'ArtifactDemo'

def rtMaven = Artifactory.newMavenBuild()

def buildInfo

def Logger

	
	stage('Git clone and setup')
	{
		try 
		{
			stageName = "Git clone and setup"
			
			checkout scm
			
			def currentDir
			
			currentDir = pwd()
			
			Logger = load("${currentDir}/pipeline-scripts/utils/Logger.groovy")
			
			Logger.info("Entering Git clone and setup stage")
			
			def moduleProp = readProperties file: 'pipeline-scripts/properties/modules.properties'

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
			catch(Exception exception) 
		{
			currentBuild.result = "FAILURE"
			Logger.error("Git clone and setup failed : $exception")
			throw exception
		}
		finally
		{
			Logger.info("Exiting Git clone and setup stage")
		}
	}
		
	stage('Build')
	{    
		try
		{
			def currentDir
			
			currentDir = pwd()
			
			Logger = load("${currentDir}/pipeline-scripts/utils/Logger.groovy")
			
			Logger.info("Entering Build stage")
			
			for(module in currentModules)
			{
				def moduleProp = readProperties file: 'pipeline-scripts/properties/modules.properties'
				
				def packagePath = moduleProp['CJP_PACKAGEPATH']
				
				println("packagePath : $packagePath")
				
				packagePathMap = MiscUtils.stringToMap(packagePath)
				
				println("packagePathMap : $packagePathMap")
				
				def packageBuildPath = MiscUtils.getBuildPath(packagePathMap,module)
								
				dir(packageBuildPath)
				{
					sh "'${mavenHome}/bin/mvn' clean install -Dmaven.test.skip=true"
				}
			}
		}
				catch(Exception exception) 
			{
				currentBuild.result = "FAILURE"
				Logger.error("Build failed : $exception")
				throw exception
			}
			finally
			{
				Logger.info("Exiting Build stage")
			}
	}
	
	stage('UTs')
	{    
		try
		{
			def currentDir
			
			currentDir = pwd()
			
			Logger = load("${currentDir}/pipeline-scripts/utils/Logger.groovy")
			
			Logger.info("Entering UTs stage")
			
			for(module in currentModules)
			{
				def moduleProp = readProperties file: 'pipeline-scripts/properties/modules.properties'
				
				def packagePath = moduleProp['CJP_PACKAGEPATH']
				
				println("packagePath : $packagePath")
				
				packagePathMap = MiscUtils.stringToMap(packagePath)
				
				println("packagePathMap : $packagePathMap")
				
				def packageBuildPath = MiscUtils.getBuildPath(packagePathMap,module)
				
				dir(packageBuildPath)
				{
					sh "'${mavenHome}/bin/mvn' clean test"
				}
			}
		}
				catch(Exception exception) 
			{
				currentBuild.result = "FAILURE"
				Logger.error("UTs failed : $exception")
				throw exception
			}
			finally
			{
				Logger.info("Exiting UTs stage")
			}
	}
	
	stage('sonarAnalysis')
	{    
		try
		{		
			def currentDir
			
			currentDir = pwd()
			
			Logger = load("${currentDir}/pipeline-scripts/utils/Logger.groovy")
			
			Logger.info("Entering SonarAnalysis stage")
			
			for(module in currentModules)
			{
				def moduleProp = readProperties file: 'pipeline-scripts/properties/modules.properties'
				
				def packagePath = moduleProp['CJP_PACKAGEPATH']
				
				packagePathMap = MiscUtils.stringToMap(packagePath)
				
				def sonarBranchName = MiscUtils.getSonarBranchName(ghprbSourceBranch)
								
				def packageBuildPath = MiscUtils.getBuildPath(packagePathMap,module)
				
				dir(packageBuildPath)
				{
					withSonarQubeEnv('SonarDemo')
					{
						
						sh "${mavenHome}/bin/mvn -Dsonar.branch.name=${sonarBranchName} sonar:sonar"
						
						//sh "'${mavenHome}/bin/mvn' sonar:sonar"
						//-Dsonar.host.url=http://35.200.203.119:9000 \
						//-Dsonar.login=bc7ed6c23eabd5e5001bcc733194bf9925c85efc"
					}
			
					println("Waiting for SonarQube Quality evaluation response")
					
					timeout(time: 1, unit: 'HOURS')
					{
						// Wait for SonarQube analysis to be completed and return quality gate status
						
						def quality = waitForQualityGate()
						
						if(quality.status != 'OK')
						{
							println("Quality gate check failed")
							
							throw new Exception("Quality Gate check failed")
						}
					}
				}
			}
		}
				catch(Exception exception) 
			{
				currentBuild.result = "FAILURE"
				Logger.error("sonarAnalysis : $exception")
				throw exception
			}
			finally
			{
				Logger.info("Exiting SonarAnalysis stage")
			}
	}
	
	stage('Packaging And Archiving') 
	{
	
		try
		{
				
				def currentDir

				currentDir = pwd()
				
				stageName = "Packaging And Archiving"

				Logger = load("${currentDir}/pipeline-scripts/utils/Logger.groovy")
				
				Logger.info("Entering stage Publish to Artifactory")
							
				MiscUtils = load("${currentDir}/pipeline-scripts/utils/MiscUtils.groovy")
				
				moduleProp = readProperties file: 'pipeline-scripts/properties/modules.properties'	
				
				commitHash =  sh( script: "git rev-parse origin/${env.GIT_BRANCH}",returnStdout: true, )
				
				gitCommit = commitHash.substring(0,7)
				
				def packageNames = moduleProp['PACKAGE_NAME']
				
				packageMap = MiscUtils.stringToMap(packageNames)
				
				tarPath = moduleProp['TAR_PATH']
				
				def tarPathMap = MiscUtils.stringToMap(tarPath)
							
			for(module in currentModules) 
			{
					def packageName = MiscUtils.getValueFromMap(packageMap,module)
					
					def moduleTarPath = MiscUtils.getTarPath(tarPathMap,module)	
					
					println("packageName : $packageName")
					
					dir(moduleTarPath)
					{
						sh"""
						#!/bin/bash
						tar cvf "${packageName}-${gitCommit}-b${buildNum}.tar" *
						"""
					}

			}
		}
			catch(Exception exception) 
			{
				currentBuild.result = "FAILURE"
				Logger.error("Packaging And Archiving : $exception")
				throw exception
			}
			finally
			{
				Logger.info("Exiting Packaging And Archiving")
			}
	}
			
	stage('Publish to Artifactory') 
	{
	
		try
		{
				def currentDir

				currentDir = pwd()
				
				stageName = "Publish to artifactory"

				Logger = load("${currentDir}/pipeline-scripts/utils/Logger.groovy")
				
				Logger.info("Entering stage Publish to Artifactory")
			
				ArtifactoryUtils = load("${currentDir}/pipeline-scripts/utils/ArtifactoryUtils.groovy")
				
				PipeConstants = load("${currentDir}/pipeline-scripts/utils/PipeConstants.groovy")
				
				MiscUtils = load("${currentDir}/pipeline-scripts/utils/MiscUtils.groovy")
				
				moduleProp = readProperties file: 'pipeline-scripts/properties/modules.properties'	
								
				def packageNames = moduleProp['PACKAGE_NAME']
				
				packageMap = MiscUtils.stringToMap(packageNames)
				
				tarPath = moduleProp['TAR_PATH']
				
				def tarPathMap = MiscUtils.stringToMap(tarPath)
							
				for(module in currentModules) 
				{
					def packageName = MiscUtils.getValueFromMap(packageMap,module)
					
					def moduleTarPath = MiscUtils.getTarPath(tarPathMap,module)	
					
					Logger.info("packageName : $packageName")
					
					dir(moduleTarPath)
					{
						script
						{
							println("packageName : $packageName")
							
							rtMaven.deployer server: server, snapshotRepo: 'libs-snapshot-local', releaseRepo: 'libs-release-local'

							buildInfo = Artifactory.newBuildInfo()
							
							buildInfo.env.capture = true
							
							def uploadSpec = """{
											"files": [{
											"pattern": "/home/rameshrangaswamy1/.jenkins/workspace/CI_CD_Demo/sau-jen/target/sau-0.0.1-SNAPSHOT.war",
											"target": "libs-release-local",
											"recursive": "true"
												  }]
											}"""
							server.upload spec: uploadSpec, buildInfo: buildInfo
							
							server.publishBuildInfo buildInfo
							
							println("/home/rameshrangaswamy1/.jenkins/workspace/CI_CD_Demo/${packageName}/target/${packageName}*.tar")
						}
					}
				}
		}
				catch(Exception exception) 
			{
				currentBuild.result = "FAILURE"
				Logger.error("Publish to Artifactory : $exception")
				throw exception
			}
			finally
			{
				Logger.info("Exiting Publish to Artifactory stage")
			}
	} 

	stage('Deployment')
	{
		try
		{
				def currentDir

				currentDir = pwd()

				Logger = load("${currentDir}/pipeline-scripts/utils/Logger.groovy")
			
				Logger.info("Entering Deployment stage")
				
				MiscUtils = load("${currentDir}/pipeline-scripts/utils/MiscUtils.groovy")
				
				moduleProp = readProperties file: 'pipeline-scripts/properties/modules.properties'	
				
				def packageNames = moduleProp['PACKAGE_NAME']
				
				packageMap = MiscUtils.stringToMap(packageNames)
				
				tarPath = moduleProp['TAR_PATH']
				
				def tarPathMap = MiscUtils.stringToMap(tarPath)
				
						for(module in currentModules) 
						{
								def packageName = MiscUtils.getValueFromMap(packageMap,module)
								
								def moduleTarPath = MiscUtils.getTarPath(tarPathMap,module)	
								
								println("packageName : $packageName")
								
							dir(moduleTarPath)
							{
								sh"""
								#!/bin/bash
								sshpass -p "12345" scp -r "${JENKINS_HOME}/workspace/${JOB_NAME}/${moduleTarPath}${packageName}" rameshrangaswamy1@34.93.239.237:~/apache-tomcat-8.5.37/webapps/
								sshpass -p "12345" ssh rameshrangaswamy1@34.93.239.237 "/home/rameshrangaswamy1/apache-tomcat-8.5.37/bin/startup.sh"
								"""
							}
						}
		}
				catch(Exception exception) 
			{
				currentBuild.result = "FAILURE"
				Logger.error("Deployment : $exception")
				throw exception
			}
			finally
			{
				Logger.info("Exiting Deployment stage")
			}
	}
	
	println("${WORKSPACE}")
	
}
