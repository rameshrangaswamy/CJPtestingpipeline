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
				sh "'${mavenHome}/bin/mvn' clean package"
			}
		}
		stage('sonarAnalysis')
		{       
			dir('walmart'){
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




