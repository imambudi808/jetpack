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

@file:Suppress("unused", "UNUSED_PARAMETER", "UNUSED_VARIABLE")

package androidx.compose.samples

import androidx.annotation.Sampled
import androidx.compose.Ambient
import androidx.compose.Composable
import androidx.compose.ambient
import androidx.compose.unaryPlus

@Sampled
fun createAmbient() {
    val ActiveUser = Ambient.of<User> { error("No active user found!") }
}

@Sampled
@Composable
fun ambientProvider() {
    @Composable
    fun App(user: User) {
        ActiveUser.Provider(value = user) {
            SomeScreen()
        }
    }
}

@Sampled
@Composable
fun someScreenSample() {
    @Composable
    fun SomeScreen() {
        UserPhoto()
    }
}

@Sampled
@Composable
fun consumeAmbient() {
    @Composable
    fun UserPhoto() {
        val user = +ambient(ActiveUser)
        ProfileIcon(src = user.profilePhotoUrl)
    }
}

private val ActiveUser = Ambient.of<User> { error("No active user found!") }

@Composable private fun SomeScreen() {}

@Composable private fun UserPhoto() {}
