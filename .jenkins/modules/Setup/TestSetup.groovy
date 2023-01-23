echo "Copying Server Artifact"

copyArtifacts(projectName: "${env.JOB_NAME}", selector: specific("${env.BUILD_NUMBER}"), flatten: true);
def distribution = "${CFG.params.profile}"  == 'web' ? 'payara-web' : 'payara'
echo "Installing Payara ${distribution}"
sh "mvn install:install-file -DgroupId=fish.payara.distributions -DartifactId=${distribution} -Dversion=${CFG.'build.version'} -Dpackaging=zip -Dfile=${pwd()}/${distribution}.zip"

echo "Unzipping Payara For Remote Use"
sh "unzip -q ${pwd()}/${distribution}.zip"

echo "Cleaning Up"
sh "rm -rf ./${distribution}.zip"