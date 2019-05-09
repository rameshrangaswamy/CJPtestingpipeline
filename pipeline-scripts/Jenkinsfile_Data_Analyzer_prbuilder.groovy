node("NODE_LABEL") 
{
def antHome
def mavenHome
		stage('SCM Checkout')
		{
				checkout scm
			          //sh "mv ~/.m2 ~/.m2_backup1"
			         // sh "rm -rf ~/.m2"
			          //sh "mkdir ~/.m2"
				//sh "rm -rf ~/.m2/repository/com/transerainc/"
				//sh "cp /home/jenkins/workspace/data_adx_test/artifactory-settings.xml ~/.m2/settings.xml"
				mavenHome = tool(name: 'maven-3.5.0', type: 'maven');
                //antHome = tool(name: 'ant-1.9.6', type: 'ant');
		}
	
	withEnv([
                'MAVEN_HOME=' + mavenHome,
		//'ANT_HOME=' + antHome,
                "PATH=${mavenHome}/bin:${env.PATH}"
		]) 
	     {
		stage('build & UT')
		{       
			dir("walmart")
			{
				//sh "mvn install -s ~/.m2/settings.xml"  
				sh "mvn clean install"
				 //sh "ant tprime installer"
				
				
//sh "ant adx"  	   ---> Build & UT's 
//sh "ant installer" ---> Packaging

			}
		}

	}
}
//
