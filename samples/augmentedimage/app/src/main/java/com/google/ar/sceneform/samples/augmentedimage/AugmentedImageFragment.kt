/*
 * Copyright 2018 Google LLC
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

package com.google.ar.sceneform.samples.augmentedimage

import android.app.ActivityManager
import android.content.Context
import android.content.res.AssetManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.google.ar.core.AugmentedImageDatabase
import com.google.ar.core.Config
import com.google.ar.core.Session
import com.google.ar.sceneform.samples.common.helpers.SnackbarHelper
import com.google.ar.sceneform.ux.ArFragment
import java.io.IOException
import java.io.InputStream

/**
 * Extend the ArFragment to customize the ARCore session configuration to include Augmented Images.
 */
class AugmentedImageFragment : ArFragment() {


    override fun onAttach(context: Context?) {
        super.onAttach(context)

        // Check for Sceneform being supported on this device.  This check will be integrated into
        // Sceneform eventually.
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {
            Log.e(TAG, "Sceneform requires Android N or later")
            activity?.let {
                SnackbarHelper.instance
                        .showError(it, "Sceneform requires Android N or later")
            }
        }

        val openGlVersionString = (context!!.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager)
                .deviceConfigurationInfo
                .glEsVersion
        if (java.lang.Double.parseDouble(openGlVersionString) < MIN_OPENGL_VERSION) {
            Log.e(TAG, "Sceneform requires OpenGL ES 3.0 or later")
            activity?.let {
                SnackbarHelper.instance
                        .showError(it, "Sceneform requires OpenGL ES 3.0 or later")
            }
        }
    }

    override fun onCreateView(
            inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val view = super.onCreateView(inflater, container, savedInstanceState)

        // Turn off the plane discovery since we're only looking for images
        planeDiscoveryController.hide()
        planeDiscoveryController.setInstructionView(null)
        arSceneView.planeRenderer.isEnabled = false
        return view
    }

    override fun getSessionConfiguration(session: Session): Config {
        val config = super.getSessionConfiguration(session)
        if (!setupAugmentedImageDatabase(config, session)) {
            activity?.let {
                SnackbarHelper.instance
                        .showError(it, "Could not setup augmented image database")
            }
        }
        return config
    }

    private fun setupAugmentedImageDatabase(config: Config, session: Session): Boolean {
        val augmentedImageDatabase: AugmentedImageDatabase

        val assetManager = if (context != null) context!!.assets else null
        if (assetManager == null) {
            Log.e(TAG, "Context is null, cannot initialize image database.")
            return false
        }

        // There are two ways to configure an AugmentedImageDatabase:
        // 1. Add Bitmap to DB directly
        // 2. Load a pre-built AugmentedImageDatabase
        // Option 2) has
        // * shorter setup time
        // * doesn't require images to be packaged in apk.
        if (USE_SINGLE_IMAGE) {
            val augmentedImageBitmap = loadAugmentedImageBitmap(assetManager) ?: return false

            augmentedImageDatabase = AugmentedImageDatabase(session)
            augmentedImageDatabase.addImage(DEFAULT_IMAGE_NAME, augmentedImageBitmap)
            // If the physical size of the image is known, you can instead use:
            //     augmentedImageDatabase.addImage("image_name", augmentedImageBitmap, widthInMeters);
            // This will improve the initial detection speed. ARCore will still actively estimate the
            // physical size of the image as it is viewed from multiple viewpoints.
        } else {
            // This is an alternative way to initialize an AugmentedImageDatabase instance,
            // load a pre-existing augmented image database.
            try {
                val inputStream =  context!!.assets.open(SAMPLE_IMAGE_DATABASE)
                augmentedImageDatabase = AugmentedImageDatabase.deserialize(session, inputStream)
            } catch (e: IOException) {
                Log.e(TAG, "IO exception loading augmented image database.", e)
                return false
            }

        }

        config.augmentedImageDatabase = augmentedImageDatabase
        return true
    }

    private fun loadAugmentedImageBitmap(assetManager: AssetManager): Bitmap? {
        try {
            val inputStream =  assetManager.open(DEFAULT_IMAGE_NAME)
            return BitmapFactory.decodeStream(inputStream)
        } catch (e: IOException) {
            Log.e(TAG, "IO exception loading augmented image bitmap.", e)
        }

        return null
    }

    companion object {
        private const val TAG = "AugmentedImageFragment"

        // This is the name of the image in the sample database.  A copy of the image is in the assets
        // directory.  Opening this image on your computer is a good quick way to test the augmented image
        // matching.
        private const val DEFAULT_IMAGE_NAME = "default.jpg"

        // This is a pre-created database containing the sample image.
        private const val SAMPLE_IMAGE_DATABASE = "sample_database.imgdb"

        // Augmented image configuration and rendering.
        // Load a single image (true) or a pre-generated image database (false).
        private const val USE_SINGLE_IMAGE = false

        // Do a runtime check for the OpenGL level available at runtime to avoid Sceneform crashing the
        // application.
        private const val MIN_OPENGL_VERSION = 3.0
    }
}
