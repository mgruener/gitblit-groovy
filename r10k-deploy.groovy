/**
 * Gitblit Post-Receive Hook: r10k-deploy
 *
 * Perform an r10k deploy environment/module
 *
 */

import com.gitblit.GitBlit
import com.gitblit.models.RepositoryModel
import com.gitblit.models.UserModel
import org.eclipse.jgit.transport.ReceiveCommand
import org.eclipse.jgit.transport.ReceiveCommand.Result
import org.slf4j.Logger

import org.eclipse.jgit.lib.Repository

logger.info("r10k-deploy hook triggered by ${user.username} for ${repository.name}: checking ${commands.size} commands")

def failed = false
def r10kCommand = gitblit.getString('r10kdeploy.command','sudo -u puppet r10k')

try {
	def r10k = """${r10kCommand} version""".execute()
	r10k.waitFor()
	logger.debug("r10k-deploy: r10k version: ${r10k.in.text}")
} catch (IOException e) {
	logger.error("r10k-deploy: Unable to execute '${r10kCommand}''")
	return false
}

Repository repo = gitblit.getRepository(repository.name)

for (command in commands) {
	try {
		def ref = command.refName
		def branch = repo.shortenRefName(command.refName)
		// only work on branches because r10k ignores everything else
		if (ref.startsWith('refs/heads/')) {
			logger.info("r10k-deploy: deploying environment ${branch}")
			def r10kdeploy = """${r10kCommand} deploy environment ${branch}""".execute()
			r10kdeploy.waitFor()
			if (r10kdeploy.exitValue()) {
				logger.error("r10k-deploy: an error occured during environment deployment! Reason ${r10kdeploy.err.text}")
			}
		} else {
			logger.debug("r10k-deploy: ${branch} (ref: ${ref}) is not a branch, not deploying")
		}
	} catch (Throwable e) {
		logger.error("r10k-deploy: an exception occured during environment deployment! Reason ${e}")
		failed = true
	}
}

repo.close()

if (failed) {
        // return false to break the push hook chain
	return false
}
