package me.serce.bazillion

import com.intellij.codeInsight.AttachSourcesProvider.AttachSourcesAction
import com.intellij.jarFinder.AbstractAttachSourceProvider
import com.intellij.jarFinder.InternetAttachSourceProvider
import com.intellij.notification.Notification
import com.intellij.notification.NotificationType
import com.intellij.openapi.application.WriteAction
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.LibraryOrderEntry
import com.intellij.openapi.roots.OrderRootType
import com.intellij.openapi.roots.libraries.Library
import com.intellij.openapi.roots.ui.configuration.LibrarySourceRootDetectorUtil
import com.intellij.openapi.util.ActionCallback
import com.intellij.openapi.vfs.JarFileSystem
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiFile
import com.intellij.util.io.HttpRequests
import me.serce.bazillion.LibManager.LibMetadata
import java.io.File
import java.io.IOException
import java.nio.file.Files

class BazilAttachSourceProvider : AbstractAttachSourceProvider() {
  private val LOG = Logger.getInstance(InternetAttachSourceProvider::class.java)

  override fun getActions(
    orderEntries: List<LibraryOrderEntry>,
    psiFile: PsiFile
  ): Collection<AttachSourcesAction> {
    val project = psiFile.project
    val libraries: HashSet<Library> = orderEntries
      .mapNotNull { it.library }
      .toHashSet()
    if (libraries.isEmpty()) {
      return emptyList()
    }

    val jar = getJarByPsiFile(psiFile) ?: return emptyList()

    val jarName = jar.nameWithoutExtension
    val index = jarName.lastIndexOf('-')
    if (index == -1) return emptyList()
    val sourceFileName = "$jarName-sources.jar"

    for (library in libraries) {
      for (file in library.getFiles(OrderRootType.SOURCES)) {
        if (file.path.contains(sourceFileName)) {
          if (isRootInExistingFile(file)) {
            return emptyList() // Sources already attached, but source-jar doesn't contain current class.
          }
        }
      }
    }

    val libCoords = libraries.first().name?.substring("Bazil: ".length) ?: return emptyList()

    val libManager = LibManager.getInstance(project)
    val lib: LibMetadata = libManager.getLibMeta(libCoords) ?: return emptyList()
    val sources = lib.source ?: return emptyList()
    val artifactUrl = sources.url

    val sourceFile = sources.localSourceJar()

    // The returned action can not be light, or otherwise it might be displaced by
    // other actions, e.g. decompile actions from the scala plugin
    return setOf<AttachSourcesAction>(object : AttachSourcesAction {
      override fun getName() = "Download Bazil Sources..."

      override fun getBusyText() = "Searching..."

      override fun perform(orderEntriesContainingFile: List<LibraryOrderEntry>): ActionCallback {
        val task = object : Task.Modal(psiFile.project, "Searching source...", true) {
          override fun run(indicator: ProgressIndicator) {
            try {
              indicator.text = "Downloading sources for ${lib.coords}"
              val tmpDownload = Files.createTempFile("bazil-dwnl-${lib.name}", "*.tmp").toFile()
              tmpDownload.deleteOnExit()
              HttpRequests.request(artifactUrl).saveToFile(tmpDownload, indicator)
              if (!sourceFile.exists() && !tmpDownload.renameTo(sourceFile)) {
                LOG.warn("Failed to rename file $tmpDownload to $sourceFileName")
              }
            } catch (e: IOException) {
              LOG.warn(e)
              showMessage(
                "Downloading failed", "Connection problem. See log for more details.",
                NotificationType.ERROR
              )
            }
          }

          override fun onSuccess() = attachSourceJar(sourceFile, libraries, project)

          private fun showMessage(title: String, message: String, notificationType: NotificationType) {
            Notification("Source searcher", title, message, notificationType).notify(getProject())
          }
        }
        task.queue()
        return ActionCallback.DONE
      }
    })
  }

  fun attachSourceJar(
    sourceJar: File,
    libraries: Collection<Library>,
    project: Project
  ) {
    val srcFile = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(sourceJar) ?: return
    val jarRoot = JarFileSystem.getInstance().getJarRootForLocalFile(srcFile) ?: return

    var roots = LibrarySourceRootDetectorUtil.scanAndSelectDetectedJavaSourceRoots(
      null,
      arrayOf(jarRoot)
    )
    if (roots.isEmpty()) {
      roots = arrayOf(jarRoot)
    }
    doAttachSourceJars(libraries, roots, project)
  }

  private fun doAttachSourceJars(
    libraries: Collection<Library>,
    roots: Array<VirtualFile>,
    project: Project
  ) {
    // Adding sources might cause a dialog to pop-up which can't be shown in the dumb mode
    DumbService.getInstance(project).runWhenSmart {
      WriteAction.run<RuntimeException> {
        for (library in libraries) {
          val model = library.modifiableModel
          val alreadyExistingFiles: HashSet<VirtualFile> = hashSetOf(*model.getFiles(OrderRootType.SOURCES))

          for (root in roots) {
            if (!alreadyExistingFiles.contains(root)) {
              model.addRoot(root, OrderRootType.SOURCES)
            }
          }
          model.commit()
        }
      }
    }
  }

  private fun isRootInExistingFile(root: VirtualFile) = when (root.fileSystem) {
    is JarFileSystem -> {
      val jar = JarFileSystem.getInstance().getVirtualFileForJar(root)
      // we might be invoked outside EDT, so sync VFS refresh is impossible, so we check java.io.File existence
      !(jar == null || !VfsUtilCore.virtualToIoFile(jar).exists())
    }
    else -> true
  }
}
