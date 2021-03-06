/*
 * Copyright (C) 2017 The Android Open Source Project
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

package androidx.room.solver.query.result

import androidx.annotation.NonNull
import androidx.room.ext.CallableTypeSpecBuilder
import androidx.room.ext.L
import androidx.room.ext.N
import androidx.room.ext.RoomTypeNames.INVALIDATION_OBSERVER
import androidx.room.ext.T
import androidx.room.ext.arrayTypeName
import androidx.room.ext.typeName
import androidx.room.solver.CodeGenScope
import com.squareup.javapoet.FieldSpec
import com.squareup.javapoet.MethodSpec
import com.squareup.javapoet.ParameterSpec
import com.squareup.javapoet.ParameterizedTypeName
import com.squareup.javapoet.TypeName
import com.squareup.javapoet.TypeSpec
import javax.lang.model.element.Modifier
import javax.lang.model.type.TypeMirror

/**
 * Converts the query into a LiveData and returns it. No query is run until necessary.
 */
class LiveDataQueryResultBinder(
    val typeArg: TypeMirror,
    val tableNames: Set<String>,
    adapter: QueryResultAdapter?
) : BaseObservableQueryResultBinder(adapter) {
    @Suppress("JoinDeclarationAndAssignment")
    override fun convertAndReturn(
        roomSQLiteQueryVar: String,
        canReleaseQuery: Boolean,
        dbField: FieldSpec,
        inTransaction: Boolean,
        scope: CodeGenScope
    ) {
        val callableImpl = CallableTypeSpecBuilder(typeArg.typeName()) {
            createRunQueryAndReturnStatements(
                builder = this,
                roomSQLiteQueryVar = roomSQLiteQueryVar,
                inTransaction = inTransaction,
                dbField = dbField,
                scope = scope,
                cancellationSignalVar = "null" // LiveData can't be cancelled
            )
        }.apply {
            if (canReleaseQuery) {
                addMethod(createFinalizeMethod(roomSQLiteQueryVar))
            }
        }.build()

        scope.builder().apply {
            val tableNamesList = tableNames.joinToString(",") { "\"$it\"" }
            addStatement(
                "return $N.getInvalidationTracker().createLiveData(new $T{$L}, $L, $L)",
                dbField,
                String::class.arrayTypeName(),
                tableNamesList,
                if (inTransaction) "true" else "false",
                callableImpl
            )
        }
    }

    private fun createComputeMethod(
        roomSQLiteQueryVar: String,
        typeName: TypeName,
        observerField: FieldSpec,
        dbField: FieldSpec,
        inTransaction: Boolean,
        scope: CodeGenScope,
        cancellationSignal: String
    ): MethodSpec {
        return MethodSpec.methodBuilder("compute").apply {
            addAnnotation(Override::class.java)
            addModifiers(Modifier.PROTECTED)
            returns(typeName)

            beginControlFlow("if ($N == null)", observerField).apply {
                addStatement("$N = $L", observerField, createAnonymousObserver())
                addStatement(
                    "$N.getInvalidationTracker().addWeakObserver($N)",
                    dbField, observerField
                )
            }
            endControlFlow()

            createRunQueryAndReturnStatements(
                builder = this,
                roomSQLiteQueryVar = roomSQLiteQueryVar,
                dbField = dbField,
                inTransaction = inTransaction,
                scope = scope,
                cancellationSignalVar = cancellationSignal
            )
        }.build()
    }

    private fun createAnonymousObserver(): TypeSpec {
        val tableNamesList = tableNames.joinToString(",") { "\"$it\"" }
        return TypeSpec.anonymousClassBuilder(tableNamesList).apply {
            superclass(INVALIDATION_OBSERVER)
            addMethod(MethodSpec.methodBuilder("onInvalidated").apply {
                returns(TypeName.VOID)
                addAnnotation(Override::class.java)
                addParameter(
                    ParameterSpec.builder(
                        ParameterizedTypeName.get(Set::class.java, String::class.java), "tables"
                    )
                        .addAnnotation(NonNull::class.java)
                        .build()
                )
                addModifiers(Modifier.PUBLIC)
                addStatement("invalidate()")
            }.build())
        }.build()
    }
}
