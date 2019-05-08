#!groovy

//analyzer/pipeline-scripts/Jenkinsfile_Data_Analyzer_servicevalidation.groovy
/**
 * This script is triggered for Service Validation
 * It is invoked by specific comments issued by reviewers on PRs
 * Comments for this script is "DATA_MERGE"
 * DATA_MERGE validates and merge changes to master branch
 * DATA_MERGE should be issued only when validations are passed
 */
 
 
@Library("ccc-pipeline-utils") _

import ArtifactoryUtils
import GitUtils
import Constants
import Logger


	// Creating objects for imported util files
	
	def ArtifactoryUtils = new ArtifactoryUtils()
	def Constants = new Constants()
	def GitUtils = new GitUtils()
	def Logger = new Logger()
	
	def currentModules
	String buildNum = currentBuild.number.toString()
	Map<String,String> previousVersions=new HashMap<>();
	Map<String,Integer> map=new HashMap<>();
	def repo
	def delimiter = "/"

	// Defining Installer variables
	def SSH_USER_NAME
	def INSTALLER_HOST
	def BASTION_HOST
	def hostName
	def hostMap
	def hostList
	
	def currentDir
	def tarPath
	def stageName
	def commitHash
	def packageMap
	def previousInstalledVersion
	def packagePathMap
	
	
	Logger.info("Entering Service validation")
	
/** Specifying node on which current build would run */
throttle(['CJP_CICD_category'])
{
	node(NODE_LABEL)
	{

		// Defining maven and ant variables 	
		Logger.info("Build trigger by $ghprbTriggerAuthor using comment $ghprbCommentBody")
		def mavenHome = tool(name: 'maven-3.5.0', type: 'maven');
		def antHome = tool(name: 'ant-1.9.6', type: 'ant');
		def MiscUtils
	
		/** Stage to clone repo from git and setup environment for build */
		stage("Git clone and setup")
		{
		try 
		{
		
			Logger.info("Entering Git clone and setup stage")
			stageName = "Git clone and setup"
			checkout scm			
			moduleProp = readProperties file: 'pipeline-scripts/properties/modules.properties'
			currentDir = pwd()
			MiscUtils = load("${currentDir}/pipeline-scripts/utils/MiscUtils.groovy")	
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
			
			//Determine optional stages
			BUILD_VARS = BUILD_VARS.toBoolean()
			Logger.info("Running optional stages is set to  : $BUILD_VARS")
			pullId = MiscUtils.extractInts(sha1)
			IS_VALIDATED = IS_VALIDATED.toBoolean()
			Logger.info("IS_VALIDATED : $IS_VALIDATED")
			if(IS_VALIDATED)
			{
				def response
				withCredentials([usernamePassword(credentialsId: "Gideal", usernameVariable: 'USER', passwordVariable: 'PASS')])
				{
					def auth_key = "${USER}:${PASS}"
					def auth_encoded = auth_key.bytes.encodeBase64().toString()
					response = httpRequest consoleLogResponseBody: true,
						customHeaders: [[name: 'Authorization', value: "Basic ${auth_encoded}"]],
						httpMode: 'GET',
						url: "${Constants.GITHUB_STATUS_URL}/${ghprbGhRepository}/issues/${pullId}/comments"

				}
				if(!MiscUtils.isValidateCommentIssued(response.content, "DATA_VALIDATE"))
				{
					Logger.error("Validate comment not issued")
					throw new Exception("Validate comment not issued")
				}
			}
				
		}
		catch(Exception exception)
		{
			currentBuild.result = "FAILURE"
			Logger.error("Git clone and setup failed : $exception")
			GitUtils.updatePrStatus(stageName,"failure",commitHash)
			throw exception
		}
		finally
		{
			Logger.info("Exiting Git clone and setup stage")
		}
		
	}
	
	if(BUILD_VARS)
	{
		/**
		* Setting up Maven Environment for Build and UT stage
		* Stage to run Build and UT's for changed modules
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
						Logger.info("Entering Build and UTs stage")
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
						Logger.error("Build and UTs failed : $exception")
						GitUtils.updatePrStatus(stageName,"failure",commitHash)
						throw exception
					}
					finally
					{
						Logger.info("Exiting Build and UT stage")
					}
				}
			}
	}
	
	if(BUILD_VARS)
	{
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
					def packagePath = moduleProp['DATA_PACKAGEPATH']
					packagePathMap = MiscUtils.stringToMap(packagePath)
					for (module in currentModules)
						{
						  def packageBuildPath = MiscUtils.getBuildPath(packagePathMap,module)
						  dir(packageBuildPath)
							{
								withSonarQubeEnv('CJPSonar') 
								{
									sh "${mavenHome}/bin/mvn -Dsonar.branch.name=${sonarBranchName} sonar:sonar"
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
							}
						}
						GitUtils.updatePrStatus(stageName,"success",commitHash)
				}
			
				catch(Exception exception) 
				{
					currentBuild.result = "FAILURE"
					Logger.error("Static analysis faild : $exception")
					GitUtils.updatePrStatus(stageName,"failure",commitHash)
					throw exception
				}
				finally
				{
					Logger.info("Exting Static Analysis stage")
				}
			}
		}
	}

	/**
	* Setting up Maven Environment for Packaging stage	
	*/

	/** Stage for Packaging changed modules*/
		
	withEnv([
					'MAVEN_HOME=' + mavenHome,
					'ANT_HOME=' + antHome,
					"PATH=${mavenHome}/bin:${antHome}/bin:${env.PATH}"
			])  
			{	
				stage ("Packaging")
				{
					try
					{
						Logger.info("Entering Package stage")
						stageName = "Packaging"
						def packagePath = moduleProp['DATA_PACKAGEPATH']
						packagePathMap = MiscUtils.stringToMap(packagePath)
						for(module in currentModules)
						 {  
							def packageBuildPath = MiscUtils.getBuildPath(packagePathMap,module)
							dir(packageBuildPath)
							{								
								Logger.info("Packaging for $module")
								sh "ant installer"
								
							}	
						 }
						 GitUtils.updatePrStatus(stageName,"success",commitHash)
					}
					catch(Exception exception) 
					{
						currentBuild.result = "FAILURE"
						Logger.error("Packaging failed : $exception")
						GitUtils.updatePrStatus(stageName,"failure",commitHash)
						throw exception
					}
					finally
					{
						Logger.info("Exiting Package stage")
					}
				} 
			}
			
		/** Stage to Deploy the changed modules to Dev environment */
		stage("Dev Deploy")
		{
			try
			{					
					Logger.info("Entering Dev Deploy stage")
					stageName = "Dev Deploy"
					SSH_USER_NAME  = moduleProp['SSH_USER_NAME']						
					INSTALLER_HOST = moduleProp['INSTALLER_HOST']						
					BASTION_HOST = moduleProp['BASTION_HOST']						
					def packageNames = moduleProp['PACKAGE_NAME']				
					packageMap = MiscUtils.stringToMap(packageNames)				
					tarPath = moduleProp['TAR_PATH']
					def tarPathMap = MiscUtils.stringToMap(tarPath)
					echo "HOST_NAME : $HOST_NAME"
					hostMap = MiscUtils.stringToMap(HOST_NAME)
					scriptPath = "${currentDir}/pipeline-scripts/utils/scripts"					
					for(module in currentModules)
					{				
						packageName = MiscUtils.getValueFromMap(packageMap,module)
						def moduleTarPath = MiscUtils.getTarPath(tarPathMap,module)							
						hostName = MiscUtils.getValueFromMap(hostMap,packageName)
						Logger.info("hostName :"+hostName)
						hostList = hostName.split('~')					
						dir(moduleTarPath) 
						{
								sh"""
								#!/bin/bash
								tar cvf "${packageName}-b${buildNum}.tar" "$packageName"
								"""
								//Get the current version of the package
								previousInstalledVersion = MiscUtils.getCurrentVersion(packageName,SSH_USER_NAME,BASTION_HOST,INSTALLER_HOST,hostList[0])
								//Added to remove \n from the version variable
								previousInstalledVersion = previousInstalledVersion.replaceAll("[\n\r]", "")
								previousVersions.put(packageName,previousInstalledVersion)
								MiscUtils.copyPackageToInstaller(packageName,SSH_USER_NAME,BASTION_HOST,INSTALLER_HOST)
								installPackage(packageName,scriptPath,buildNum,SSH_USER_NAME,BASTION_HOST,INSTALLER_HOST,hostName)
								

								
						}						
						
					}
					GitUtils.updatePrStatus(stageName,"success",commitHash)									
																	
					
			}
			catch(Exception exception) 
			{
				currentBuild.result = "FAILURE"					
				Logger.error("Dev Deploy failed : $exception")
				GitUtils.updatePrStatus(stageName,"failure",commitHash)					
				throw exception
			}
			finally
			{
				Logger.info("Exiting Dev Deploy stage")
			}
		}
		
		/** Stage to do functional tests for the Deployed modules */		
		stage("Functional Test")
		{
		try
			{
				Logger.info("Entering stage Functional Test")
				stageName = "Functional Test"
				for(modules in currentModules)
					{
						withCredentials([file(credentialsId: 'devus1-ubuntu-pem', variable: 'jenkinsKeyFile')]) 
						{
							packageName = MiscUtils.getValueFromMap(packageMap,modules)																
							hostName = MiscUtils.getValueFromMap(hostMap,packageName)
							hostList = hostName.split('~')
							host = MiscUtils.getConsulAddress(hostList[0])								
							sh """
								#!/bin/bash									
								ssh -i $jenkinsKeyFile -o StrictHostKeyChecking=no $SSH_USER_NAME@$BASTION_HOST
								ssh -i $jenkinsKeyFile $SSH_USER_NAME@$host -o StrictHostKeyChecking=no -o "proxycommand ssh -W %h:%p -i $jenkinsKeyFile $SSH_USER_NAME@$BASTION_HOST" \
								"ps -ef |grep -i $modules"
								[ \$? -ne 0 ] && exit 1
								exit 0
							"""
						}
						
					}
					GitUtils.updatePrStatus(stageName,"success",commitHash)						
			}
			catch (Exception exception) {
			/** Stage for Rollback if functional tests fails */
			stage('Rollback') {
				Logger.info("Entering Rollback stage")
				for(Map.Entry m:previousVersions.entrySet())
				{
					packageName = m.getKey()
					def buildNumber = m.getValue()
					def previousBuildNumber = buildNumber.split("\\.")[3]
					hostName = MiscUtils.getValueFromMap(hostMap,packageName)					
					installPackage(packageName,scriptPath,previousBuildNumber,SSH_USER_NAME,BASTION_HOST,INSTALLER_HOST,hostName)
					Logger.info(" Rollback successfully completed to previous vesion $previousInstalledVersion")
				}
				Logger.info("Exiting Rollback stage")
			}
				GitUtils.updatePrStatus(stageName,"failure",commitHash)				
				throw exception
			}
			finally
			{
				Logger.info("Exiting stage Functional Test")
			}
		}

	

	/** Stage to merge PRs with master*/	
	stage("Merge to Master") 
	{
		try
		{
			Logger.info("Entering Merge to Master Stage")
			stageName = "Merge to Master"
			currentBuild.result = "SUCCESS"				
			checkout scm
			Logger.info("Merging pull request with master")
			MiscUtils.mergePullRequest()					
					
		}
		catch(Exception exception) 
		{
			currentBuild.result = "FAILURE"			
			Logger.error("Merge failed with $exception")			
			throw exception
		}
		finally
		{			
			Logger.info("Exiting Merge Stage")
			
		}
		
	}
  }
}		
	
checkpoint 'build_master'
node(NODE_LABEL)
	{
	
	def mavenHome = tool(name: 'maven-3.5.0', type: 'maven');
	def antHome = tool(name: 'ant-1.9.6', type: 'ant');
	def CjpArtifactoryUtils
	def CjpConstants
	def gitCommit
	def MiscUtils
	def checkpointName
	
	/**Stage to Run Build and UT's on Master Branch*/
	
	withEnv([
                'MAVEN_HOME=' + mavenHome,
				'ANT_HOME=' + antHome,
                "PATH=${mavenHome}/bin:${antHome}/bin:${env.PATH}"
		])  
		{
			stage ("Build and UTs on Master") 
			{
				try
				{
					Logger.info("Entering to Build and UT Stage")
					stageName = "Build and UTs on master"
					checkpointName = "build_master"
					repo = ghprbGhRepository
					def commitBranch = "origin/${ghprbTargetBranch}"
					GitUtils.gitCheckout(ghprbGhRepository,ghprbTargetBranch)
					dir(repo)
					{	
						currentDir = pwd()
						CjpArtifactoryUtils = load("${currentDir}/pipeline-scripts/utils/CjpArtifactoryUtils.groovy")
						CjpConstants = load("${currentDir}/pipeline-scripts/utils/CjpConstants.groovy")
						MiscUtils = load("${currentDir}/pipeline-scripts/utils/MiscUtils.groovy")
						moduleProp = readProperties file: 'pipeline-scripts/properties/modules.properties'				
						commitHash =  sh( script: "git rev-parse ${commitBranch}", returnStdout: true )
						gitCommit = commitHash.substring(0,7)
						Logger.info("Service modules changed : $currentModules")
						MiscUtils.setDisplayName(buildNum, currentModules)				
					}   
						def buildCommand = moduleProp['DATA_ANT_MODULES']
						def buildCommandMap = MiscUtils.stringToMap(buildCommand)
						def packagePath = moduleProp['DATA_PACKAGEPATH']
						packagePathMap = MiscUtils.stringToMap(packagePath)
						for(module in currentModules) 
						{
							def packageBuildPath = MiscUtils.getBuildPath(packagePathMap,module)
							def command = MiscUtils.getBuildCommand(buildCommandMap,module)								
							def buildPath = "${repo}${delimiter}${packageBuildPath}"
							dir(buildPath) 
							{								
								Logger.info("Running UTs for $module")
								sh "$command"
								
							}
							
						}
										
				
				}
				catch(Exception exception) 
				{
					currentBuild.result = "FAILURE"
					Logger.info("Stage Build & UTs failed with error : $exception")
					MiscUtils.sendEmail("FAILED",stageName,checkpointName)						
					throw exception
				}
				finally
				{
					Logger.info("Exiting stage Build and UT ")
				}
				
			}
		}
	
	withEnv([
                'MAVEN_HOME=' + mavenHome,
				'ANT_HOME=' + antHome,
                "PATH=${mavenHome}/bin:${antHome}/bin:${env.PATH}"
		])  
	{
	
	/** Stage to invoke Static Code Analyzer SonarQube */
		
		stage('Static Analysis') 
		{
			try
			{
				Logger.info("Entering stage Static Analysis")
				stageName = "Static Analysis"
				def packagePath = moduleProp['DATA_PACKAGEPATH']
				packagePathMap = MiscUtils.stringToMap(packagePath)
				def sonarBranchName = MiscUtils.getSonarBranchName(ghprbTargetBranch)
				//This name should match with SonarQube Server name provided in Jenkins configuration
					
					for (module in currentModules)
					{
						def packageBuildPath = MiscUtils.getBuildPath(packagePathMap,module)								
						def buildPath = "${repo}${delimiter}${packageBuildPath}"
						dir(buildPath)
						{
							withSonarQubeEnv('CJPSonar') 
							{
								sh "${mavenHome}/bin/mvn -Dsonar.branch.name=${sonarBranchName} sonar:sonar"
							}
							Logger.info("Waiting for SonarQube Quality evaluation response")
							timeout(time: 1, unit: 'HOURS')
							{
								// Wait for SonarQube analysis to be completed and return quality gate status
								def quality = waitForQualityGate()
								if(quality.status != 'OK')
								{
									Logger.error("Quality Gate checks failed")
									throw new Exception("Quality Gate checks failed")
								}
								Logger.info("Quality Gate Checks passed ")
								
								
							}
						}
					}
									
				
			}
		
			catch(Exception exception)
			{
				currentBuild.result = "FAILURE"
				Logger.error("Stage Static Analysis failed with error : $exception")
				MiscUtils.sendEmail("FAILED",stageName,checkpointName)
				throw exception
			}
			finally
			{
				Logger.info("Exiting stage Static Analysis")
			}
		}
	}
	
					
		/**
		* Setting up Maven Environment for Packaging stage	
		*/

		/** Stage for Packaging changed modules*/
	
		withEnv([
						'MAVEN_HOME=' + mavenHome,
						'ANT_HOME=' + antHome,
						"PATH=${mavenHome}/bin:${antHome}/bin:${env.PATH}"
				])  
		{	
			stage ("Packaging Master")
			{
				try
				{
					Logger.info("Entering Package stage")
					stageName = "Packaging Master"
					def packagePath = moduleProp['DATA_PACKAGEPATH']
					packagePathMap = MiscUtils.stringToMap(packagePath)
					for(module in currentModules)
					 {
						def packageBuildPath = MiscUtils.getBuildPath(packagePathMap,module)						
						def buildPath = "${repo}${delimiter}${packageBuildPath}"
						dir(buildPath)
						{							
							Logger.info("Packaging for $module")
							sh "ant installer"
							
						}	
					 }

				}
				catch(Exception exception) 
				{
					currentBuild.result = "FAILURE"
					Logger.error("Packaging failed : $exception")
					MiscUtils.sendEmail("FAILED",stageName,checkpointName)
					throw exception
				}
				finally
				{
					Logger.info("Exiting Package stage")
				}
			} 
		}
	
	/** Stage to publish to artifactory */
	stage('Publish to Artifactory') 
	{
	
		try
		{
				Logger.info("Entering stage Publish to Artifactory")
				stageName = "Publish to artifactory"
				def packageNames = moduleProp['PACKAGE_NAME']
				packageMap = MiscUtils.stringToMap(packageNames)
				tarPath = moduleProp['TAR_PATH']
				def tarPathMap = MiscUtils.stringToMap(tarPath)				
				for(module in currentModules) 
					{
						def packageName = MiscUtils.getValueFromMap(packageMap,module)
						def moduleTarPath = MiscUtils.getTarPath(tarPathMap,module)						
						def masterTarPath = "${repo}${delimiter}${moduleTarPath}"						
						dir(masterTarPath)
						{
							sh"""
							#!/bin/bash
							tar cvf "${packageName}-${gitCommit}-b${buildNum}.tar" "$packageName"
							"""
							CjpArtifactoryUtils.publishCcOneAppPackageMaster(CjpConstants.ARTIFACTORY_REPO, packageName, buildNum)
								
						}			
					}
					
				
				
		}
		catch(Exception exception) 
		{
			currentBuild.result = "FAILURE"
			Logger.info("stage Publish to Artifactory failed : $exception")
			MiscUtils.sendEmail("FAILED",stageName,checkpointName)
			throw exception
		}
		finally
		{
			Logger.info("Exiting stage Publish to Artifactory")
		}
    }
	
	stage('Remove Package')
	{
		try
		{
			Logger.info("Entering stage Remove the previousInstalled Package")
			stageName = "removePreviousInstalledPackage"
			for(Map.Entry m:previousVersions.entrySet())
			{
				packageName = m.getKey()
				def previousVersion = m.getValue()
				hostName = MiscUtils.getValueFromMap(hostMap,packageName)
				def packageList = MiscUtils.validatePackageForDeletion(packageName,SSH_USER_NAME,BASTION_HOST,INSTALLER_HOST)
				packageList = packageList.replaceAll("[\n\r]", "")
				def versionList = packageList.split(" ")
				Logger.info(" Info on the package versions :: $versionList ")

				if(versionList.contains(previousVersion))
				{
					Logger.info(" Package cannot be removed as present in some host")
					Logger.info("$packageName $previousVersion ")
				}
				else
				{
					Logger.info(" Package can be removed as not present in some host")
					Logger.info("$packageName $previousVersion ")
					MiscUtils.removePackageOnInstaller(packageName,previousVersion,SSH_USER_NAME,BASTION_HOST,INSTALLER_HOST)
					
				}
				Logger.info(" Removed the previous Installed Package $previousInstalledVersion")
			}				
		}
			catch(Exception exception) 
			{
				currentBuild.result = "FAILURE"
				Logger.error("Packaging removed : $exception")
				MiscUtils.sendEmail("FAILED",stageName,checkpointName)
				throw exception
			}
			finally
			{
				Logger.info("Exiting stage Remove Package")
			}
	}
	
	
	Logger.info("Exiting Service validation")
}




// Function to run the Installer upgrade script 
//Note: This function cant be moved to utils because it uses the local shell script upgrade.sh
def installPackage(packageName,scriptPath,buildNum,SSH_USER_NAME,BASTION_HOST,INSTALLER_HOST,hostName)
{
	withCredentials([file(credentialsId: 'devus1-ubuntu-pem', variable: 'jenkinsKeyFile')]) {
        sh """
            #!/bin/bash
			ssh -i $jenkinsKeyFile -o StrictHostKeyChecking=no $SSH_USER_NAME@$BASTION_HOST
			scp -i $jenkinsKeyFile -o StrictHostKeyChecking=no -o "proxycommand ssh -i $jenkinsKeyFile -W %h:%p $SSH_USER_NAME@$BASTION_HOST" \
			$scriptPath/upgrade.sh $SSH_USER_NAME@$INSTALLER_HOST:/tmp/test_upgrade.sh
			[ \$? -ne 0 ] && exit 1			
			ssh -i $jenkinsKeyFile $SSH_USER_NAME@$INSTALLER_HOST -o StrictHostKeyChecking=no -o "proxycommand ssh -W %h:%p -i $jenkinsKeyFile $SSH_USER_NAME@$BASTION_HOST" \
			'sudo su -c "chmod 777 /tmp/test_upgrade.sh;sh /tmp/test_upgrade.sh $packageName 10.0.0.$buildNum $hostName;rm -f /tmp/test_upgrade.sh" '
			[ \$? -ne 0 ] && exit 1
			exit 0
        """
    }
}

