package com.intellij.deno.service

import com.intellij.codeInsight.completion.CompletionParameters
import com.intellij.deno.DenoSettings
import com.intellij.lang.javascript.DialectDetector
import com.intellij.lang.javascript.dialects.TypeScriptLanguageDialect
import com.intellij.lang.javascript.integration.JSAnnotationError
import com.intellij.lang.javascript.integration.JSAnnotationError.*
import com.intellij.lang.javascript.psi.JSFunctionType
import com.intellij.lang.javascript.service.JSLanguageServiceProvider
import com.intellij.lang.parameterInfo.CreateParameterInfoContext
import com.intellij.lang.typescript.compiler.TypeScriptCompilerSettings
import com.intellij.lang.typescript.compiler.TypeScriptService
import com.intellij.lang.typescript.compiler.TypeScriptService.CompletionEntry
import com.intellij.lang.typescript.compiler.TypeScriptService.CompletionMergeStrategy.MERGE
import com.intellij.lang.typescript.compiler.TypeScriptService.CompletionMergeStrategy.NON
import com.intellij.lsp.LspServerDescriptor
import com.intellij.lsp.LspServerManager
import com.intellij.lsp.data.LspDiagnostic
import com.intellij.lsp.data.LspSeverity.*
import com.intellij.lsp.methods.ForceDidChangeMethod
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.editor.Document
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletableFuture.completedFuture
import java.util.concurrent.Future
import java.util.stream.Stream

class DenoTypeScriptServiceProvider(val project: Project) : JSLanguageServiceProvider {

  override fun isHighlightingCandidate(file: VirtualFile) = TypeScriptCompilerSettings.acceptFileType(file.fileType)

  override fun getService(file: VirtualFile) = allServices.firstOrNull()

  override fun getAllServices() =
    if (DenoSettings.getService(project).isUseDeno()) listOf(DenoTypeScriptService.getInstance(project)) else emptyList()
}

class DenoTypeScriptService(private val project: Project) : TypeScriptService {
  companion object {
    private val LOG = Logger.getInstance(DenoTypeScriptService::class.java)
    fun getInstance(project: Project): DenoTypeScriptService = project.getService(DenoTypeScriptService::class.java)
  }

  private var descriptor: LspServerDescriptor? = null

  private fun createDescriptor(element: PsiElement) =
    LspServerManager.getServerDescriptors(element).find { it is DenoLspServerDescriptor }

  private fun getDescriptor(element: PsiElement) = descriptor ?: createDescriptor(element)

  override fun isDisabledByContext(context: VirtualFile) = false

  override fun getCompletionMergeStrategy(parameters: CompletionParameters, file: PsiFile, context: PsiElement) =
    if (parameters.isExtendedCompletion) NON else MERGE

  // TODO
  override fun updateAndGetCompletionItems(virtualFile: VirtualFile, parameters: CompletionParameters): Future<List<CompletionEntry>?>? {
    return null
  }

  // TODO
  override fun getDetailedCompletionItems(virtualFile: VirtualFile,
                                          items: List<CompletionEntry>,
                                          document: Document,
                                          positionInFileOffset: Int): Future<List<CompletionEntry>?>? = null

  // TODO
  override fun getNavigationFor(document: Document, sourceElement: PsiElement): Array<PsiElement>? = null

  // TODO
  override fun getSignatureHelp(file: PsiFile, context: CreateParameterInfoContext): Future<Stream<JSFunctionType>?>? = null

  // TODO
  override fun getQuickInfoAt(element: PsiElement, originalElement: PsiElement, originalFile: VirtualFile): CompletableFuture<String?> =
    completedFuture(null)

  override fun terminateStartedProcess() {
    descriptor?.stopServer()
  }

  override fun highlight(file: PsiFile): CompletableFuture<List<JSAnnotationError>>? {
    LOG.info("highlight")
    val server = getDescriptor(file)?.server
    val virtualFile = file.virtualFile
    FileDocumentManager.getInstance().getDocument(virtualFile)?.let {
      server?.invoke(ForceDidChangeMethod(virtualFile, it))
    }
    return completedFuture(server?.getDiagnostics(virtualFile)?.map {
      DenoAnnotationError(it, virtualFile.canonicalPath)
    })
  }

  override fun canHighlight(file: PsiFile) = DialectDetector.isTypeScript(file)

  override fun isAcceptable(file: VirtualFile) = DialectDetector.getLanguageDialect(file, project) is TypeScriptLanguageDialect
}

class DenoAnnotationError(private val diagnostic: LspDiagnostic, private val path: String?) : JSAnnotationError {
  override fun getLine() = diagnostic.range.start.line

  override fun getColumn() = diagnostic.range.start.character

  override fun getAbsoluteFilePath(): String? = path

  override fun getDescription(): String = diagnostic.message

  override fun getCategory() = when (diagnostic.severity) {
    Error -> ERROR_CATEGORY
    Warning -> WARNING_CATEGORY
    Hint, Information -> INFO_CATEGORY
  }
}