echo "Copying Server Artifact"

copyArtifacts(projectName: "${env.JOB_NAME}", selector: specific("${env.BUILD_NUMBER}"), flatten: true);
def distribution = "${CFG.params.profile}"  == 'web' ? 'payara-web.zip' : 'payara.zip'
echo "Installing Payara ${distribution}"
sh "ls"
sh "mv ${distribution} ./bundles"