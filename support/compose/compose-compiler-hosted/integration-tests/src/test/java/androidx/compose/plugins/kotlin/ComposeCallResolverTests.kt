/*
 * Copyright 2019 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package androidx.compose.plugins.kotlin

import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtPsiFactory
import org.jetbrains.kotlin.resolve.BindingContext
import org.jetbrains.kotlin.resolve.calls.callUtil.getResolvedCall
import org.jetbrains.kotlin.resolve.calls.model.ResolvedCall
import kotlin.reflect.KClass

class ComposeCallResolverTests : AbstractCodegenTest() {

    fun testBasicCallTypes() = assertInterceptions(
        """
            import androidx.compose.*
            import android.widget.TextView

            @Composable fun Foo() {}

            fun Bar() {}

            @Composable
            fun test() {
                <call>Foo()
                <emit>TextView(text="text")
                <normal>Bar()
            }
        """
    )

    fun testReceiverScopeCall() = assertInterceptions(
        """
            import androidx.compose.*

            @Composable fun Int.Foo() {}

            @Composable
            fun test() {
                val x = 1
                x.<call>Foo()

                with(x) {
                    <call>Foo()
                }
            }
        """
    )

    fun testInvokeOperatorCall() = assertInterceptions(
        """
            import androidx.compose.*

            @Composable operator fun Int.invoke(y: Int) {}

            @Composable
            fun test() {
                val x = 1
                <call>x(y=10)
            }
        """
    )

    private fun <T> setup(block: () -> T): T {
        return block()
    }

    fun assertInterceptions(srcText: String) = setup {
        val (text, carets) = extractCarets(srcText)

        val environment = myEnvironment ?: error("Environment not initialized")

        val ktFile = KtPsiFactory(environment.project).createFile(text)
        val bindingContext = JvmResolveUtil.analyze(
            ktFile,
            environment
        ).bindingContext

        carets.forEachIndexed { index, (offset, calltype) ->
            val resolvedCall = resolvedCallAtOffset(bindingContext, ktFile, offset)
                ?: error("No resolved call found at index: $index, offset: $offset. Expected " +
                    "$calltype.")

            when (calltype) {
                "<normal>" -> assert(!resolvedCall.isCall() && !resolvedCall.isEmit())
                "<emit>" -> assert(resolvedCall.isEmit())
                "<call>" -> assert(resolvedCall.isCall())
                else -> error("Call type of $calltype not recognized.")
            }
        }
    }

    private fun ResolvedCall<*>.isEmit(): Boolean = candidateDescriptor is ComposableEmitDescriptor
    private fun ResolvedCall<*>.isCall(): Boolean =
        candidateDescriptor is ComposableFunctionDescriptor

    private val callPattern = Regex("(<normal>)|(<emit>)|(<call>)")
    private fun extractCarets(text: String): Pair<String, List<Pair<Int, String>>> {
        val indices = mutableListOf<Pair<Int, String>>()
        var offset = 0
        val src = callPattern.replace(text) {
            indices.add(it.range.first - offset to it.value)
            offset += it.range.last - it.range.first + 1
            ""
        }
        return src to indices
    }

    private fun resolvedCallAtOffset(
        bindingContext: BindingContext,
        jetFile: KtFile,
        index: Int
    ): ResolvedCall<*>? {
        val element = jetFile.findElementAt(index)!!
        val callExpression = element.parentOfType<KtCallExpression>()
        return callExpression?.getResolvedCall(bindingContext)
    }
}

private inline fun <reified T : PsiElement> PsiElement.parentOfType(): T? = parentOfType(T::class)

private fun <T : PsiElement> PsiElement.parentOfType(vararg classes: KClass<out T>): T? {
    return PsiTreeUtil.getParentOfType(this, *classes.map { it.java }.toTypedArray())
}