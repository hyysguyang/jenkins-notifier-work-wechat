package com.lifecosys.jenkins.plugin

import hudson.Extension
import hudson.FilePath
import hudson.Launcher
import hudson.model.*
import hudson.scm.ChangeLogSet
import hudson.tasks.BuildStepDescriptor
import hudson.tasks.Notifier
import hudson.tasks.Publisher
import jenkins.model.Jenkins
import jenkins.tasks.SimpleBuildStep
import org.kohsuke.stapler.DataBoundConstructor


/**
 *
 * @author [Young Gu](mailto:hyysguyang@gmail.com)
 */

class WorkWechatNotifier @DataBoundConstructor constructor(val workWechatRobotKey: String, val notifyEveryUnstableBuild: Boolean, val sendToIndividuals: Boolean) : Notifier(), SimpleBuildStep {

    override fun perform(build: Run<*, *>, workspace: FilePath, launcher: Launcher, listener: TaskListener) {
        listener.logger.println("Running lifecosys jenkins Work Wechat notification ...............")
        build as AbstractBuild<*, *>
        if (build.getResult()!!.isWorseThan(Result.SUCCESS)) {
            val users = build.getCulprits()
            val changeSetLines = build.getCumulatedChangeSetSincePreviousSuccessBuild().map { "${it.author} --> ${it.commitId}: ${it.msgEscaped}" }
            val changeLogs = changeSetLines.joinToString("\n")
            listener.logger.println("Build failed by users: ${users.map { it.displayName }.joinToString()}")
            listener.logger.println("\n\nBuild failed by Commit: ${changeLogs}\n\n")

            val workWechatRobotClient = WorkWechatRobotClient(workWechatRobotKey)
            val mentionUsers = if (users.isEmpty()) "all" else users.map { "@" + it.displayName }.joinToString(" ")
            workWechatRobotClient.sendBuildMessage(build.getProject().getDisplayName(), mentionUsers, build.getAbsoluteUrl(), changeSetLines.take(5).joinToString("\n"))
            workWechatRobotClient.sendFileMessage(build.getLogFile(), "${build.getProject().getName()}-build-${build.getNumber()}.log")
            listener.logger.println("Work Wechat notify completed")
        }
    }

    fun descriptor(): DescriptorImpl {
        val jenkins = Jenkins.getInstance() ?: throw IllegalStateException("Jenkins instance is not ready")
        return jenkins.getDescriptorByType(DescriptorImpl::class.java)
    }

    @Extension
    class DescriptorImpl : BuildStepDescriptor<Publisher>() {
        override fun getDisplayName(): String = "Work Wechat Notification"
        override fun isApplicable(jobType: Class<out AbstractProject<*, *>?>?): Boolean = true
    }
}

fun AbstractBuild<*, *>.getCumulatedChangeSetSincePreviousSuccessBuild(): List<ChangeLogSet.Entry> =
        if (this.getResult()!!.isBetterOrEqualTo(Result.SUCCESS)) emptyList()
        else previousBuildChangeSet() + this.getChangeSet().toList()

fun AbstractBuild<*, *>.previousBuildChangeSet(): List<ChangeLogSet.Entry> {
    this.getPreviousBuild() as AbstractBuild<*, *>
    return getPreviousBuild()?.getCumulatedChangeSetSincePreviousSuccessBuild()?.toList<ChangeLogSet.Entry>() ?: emptyList()
}

