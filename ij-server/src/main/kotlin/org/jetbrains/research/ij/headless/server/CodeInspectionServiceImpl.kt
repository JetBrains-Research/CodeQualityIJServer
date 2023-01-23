package org.jetbrains.research.ij.headless.server

import com.intellij.codeInspection.ProblemDescriptorUtil
import com.intellij.lang.Language
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.Logger
import java.nio.file.Path
import java.util.concurrent.atomic.AtomicReference

class CodeInspectionServiceImpl(templatesPath: Path) :
    CodeInspectionServiceGrpcKt.CodeInspectionServiceCoroutineImplBase() {

    private val logger = Logger.getInstance(javaClass)

    private val psiFileManager = PsiFileManager(templatesPath)

    override suspend fun inspect(request: Code): InspectionResult {
        logger.info("Receive request: $request")

        val languageId = request.languageId.name
        val language = Language.findLanguageByID(request.languageId.name)
            ?: error("No such language by id $languageId")
        val file = psiFileManager.getPsiFile(language, request.text)

        val response = AtomicReference<InspectionResult>()

        ApplicationManager.getApplication().invokeAndWait {
            val result = IJCodeInspector.inspect(file)
            response.set(
                InspectionResult.newBuilder().addAllProblems(
                    result.flatMap { (inspection, descriptors) ->
                        descriptors.map { descriptor ->
                            Problem.newBuilder()
                                .setName(
                                    ProblemDescriptorUtil.renderDescriptionMessage(
                                        descriptor,
                                        descriptor.psiElement
                                    )
                                )
                                .setInspector(inspection.shortName)
                                .setLineNumber(descriptor.lineNumber.toLong())
                                .setOffset(descriptor.psiElement?.textOffset?.toLong() ?: -1L)
                                .setLength(descriptor.psiElement?.textLength?.toLong() ?: -1L)
                                .build()
                        }
                    }
                ).build()
            )
        }

        return response.get()
    }
}
