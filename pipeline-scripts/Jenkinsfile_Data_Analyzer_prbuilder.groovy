node("NODE_LABEL") 
{
def mavenHome = tool 'maven';
		stage('SCM Checkout')
		{
			checkout scm
		}
		stage('build & UT')
		{       
			dir('walmart'){
				sh "mvn clean install"
			}
		}

}




