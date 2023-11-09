/*
 * Copyright 2020 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.samsung.android.scan3d.fragments

import android.Manifest
import android.Manifest.permission.*
import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.util.Size
import android.view.LayoutInflater
import android.view.SurfaceHolder
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.NavController
import androidx.navigation.Navigation
import com.example.android.camera.utils.OrientationLiveData
import com.samsung.android.scan3d.CameraActivity
import com.samsung.android.scan3d.KILL_THE_APP
import com.samsung.android.scan3d.R
import com.samsung.android.scan3d.databinding.FragmentCameraBinding
import com.samsung.android.scan3d.hotspotServices.createHotspotEnabler
import com.samsung.android.scan3d.locationServices.LocationService
import com.samsung.android.scan3d.serv.CamEngine
import com.samsung.android.scan3d.serv.CameraActionState
import com.samsung.android.scan3d.serv.CameraActionState.NEW_VIEW_STATE
import com.samsung.android.scan3d.util.ClipboardUtil
import com.samsung.android.scan3d.util.IpUtil
import com.samsung.android.scan3d.util.Selector
import com.samsung.android.scan3d.util.isAllPermissionsGranted
import com.samsung.android.scan3d.util.requestPermissionList
import com.samsung.android.scan3d.webserver.WebServer
import kotlinx.coroutines.launch

class CameraFragment : Fragment() {

    private var _binding: FragmentCameraBinding? = null
    private val binding get() = _binding!!
    private val viewModel: CameraViewModel by viewModels()

//    /**
    /** AndroidX navigation arguments */
    //  private val args: CameraFragmentArgs by navArgs()

    private var resolutionWidth = DEFAULT_WIDTH
    private var resolutionHeight = DEFAULT_HEIGHT

    private lateinit var cameraActivity: CameraActivity
    /** Live data listener for changes in the device orientation relative to the camera */
    private lateinit var relativeOrientation: OrientationLiveData


    private var isServiceRunning: Boolean = false
    private var isUpdatingSwitch: Boolean = false
    private lateinit var serviceIntent: Intent

    private var hotspotEnabled = false


    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentCameraBinding.inflate(inflater, container, false)
        return binding.root
    }

    private val receiver: BroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
//            intent.extras?.getParcelable<CamEngine.Companion.DataQuick>("dataQuick")?.let {
//                binding.qualFeedback.text = " " + it.rateKbs + "kB/sec"
//                binding.ftFeedback.text = " " + it.ms + "ms"
//            }

            intent.extras?.getParcelable<CamEngine.Companion.Data>("data")?.let {
                setViews(it)
            } ?: run { return }
        }

        private fun setViews(data: CamEngine.Companion.Data) {
            val resolution = data.resolutions[data.resolutionSelected]
            resolutionWidth = resolution.width
            resolutionHeight = resolution.height
//            binding.viewFinder.setAspectRatio(resolutionWidth, resolutionHeight)
            setSwitchListeners()
//            setSpinnerCam(data)
//            setSpinnerQua()
//            setSpinnerRes(data)
            viewModel.uiState.value.cameraId = data.sensors[0].cameraId
            viewModel.uiState.value.resolutionIndex = data.resolutionSelected

        }

        private fun setSwitchListeners() {
            hotspotEnabled = !hotspotEnabled

            binding.switch1.setOnCheckedChangeListener { _, prev ->
                binding.switch1.isEnabled = false
                isUpdatingSwitch = true
                //todo convert all this into a proper service
                isServiceRunning = if (prev) {
                    createHotspotEnabler(requireContext()).let {
                        it.enableTethering()
                    }
                    true
                } else {
                    createHotspotEnabler(requireContext()).let {
                        it.disableTethering()
                    }
                    false
                }

                val handler = Handler(Looper.getMainLooper())
                handler.post(Runnable {
                    binding.switch1.isChecked = isServiceRunning
                    binding.switch1.isEnabled = true
                    isUpdatingSwitch = false
                })
            }

            serviceIntent = Intent(requireContext(), LocationService::class.java)
            binding.switch2.setOnCheckedChangeListener { _, prev ->
                binding.switch2.isEnabled = false
                isUpdatingSwitch = true
                viewModel.uiState.value.stream = prev
                sendViewState()
                isServiceRunning = if (prev) {
                    Intent(requireContext().applicationContext, LocationService::class.java).apply {
                        LocationService.webServer = WebServer()
                        action = LocationService.ACTION_START
                        requireContext().startService(this)
                    }
                    true
                } else {
                    Intent(requireContext().applicationContext, LocationService::class.java).apply {
                        action = LocationService.ACTION_STOP
                        requireContext().startService(this)
                    }
                    false
                }

                val handler = Handler(Looper.getMainLooper())
                handler.post(Runnable {
                    binding.switch2.isChecked = isServiceRunning
                    binding.switch2.isEnabled = true
                    isUpdatingSwitch = false
                })
            }
        }




    }

    fun sendViewState() {
        cameraActivity.setCameraForegroundServiceState(NEW_VIEW_STATE) {
            it.putExtra("data", viewModel.uiState.value)
        }
    }

    override fun onPause() {
        super.onPause()
        Log.i("onPause", "onPause")
        requireActivity().unregisterReceiver(receiver)
    }

    @SuppressLint("UnspecifiedRegisterReceiverFlag")
    override fun onResume() {
        super.onResume()
        Log.i("onResume", "onResume")
        requireActivity().registerReceiver(receiver, IntentFilter("UpdateFromCameraEngine"))
    }

    @SuppressLint("MissingPermission")
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        Log.i("onViewCreated", "onViewCreated")

        requestPermissionList(getPermissionsRequest(), REQUIRED_PERMISSIONS)
        cameraActivity = requireActivity() as CameraActivity

//        initViews()
        setObservers()

    }

    private fun initViews() {
//        setViews()
//        setListeners()
        setObservers()
    }

//    private fun setViews() = with(binding) {
//        textView6.text = "${IpUtil.getLocalIpAddress()}:8080/cam.mjpeg"
//        viewFinder.holder.addCallback(object : SurfaceHolder.Callback {
//            override fun surfaceDestroyed(holder: SurfaceHolder) = Unit
//            override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) = Unit
//            override fun surfaceCreated(holder: SurfaceHolder) {
//                viewFinder.setAspectRatio(resolutionWidth, resolutionHeight)
//                if (viewModel.isPermissionsGranted.value == true) {
//                    cameraActivity.setCameraForegroundServiceState(CameraActionState.NEW_PREVIEW_SURFACE) {
//                        it.putExtra("surface", viewFinder.holder.surface)
//                    }
//                }
//            }
//        })
//    }

    private fun setObservers() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.isPermissionsGranted.collect {
                    it?.let { isGranted ->
                        if (isGranted) {
                            cameraActivity.setCameraForegroundServiceState(CameraActionState.START_ENGINE)
//                            cameraActivity.setCameraForegroundServiceState(CameraActionState.NEW_PREVIEW_SURFACE) { ca ->
////                                ca.putExtra("surface", binding.viewFinder.holder.surface)
//                            }
                            // engine.start(requireContext())
                        } else {
                            Toast.makeText(
                                context,
                                "Permission should be accepted to delete download videos",
                                Toast.LENGTH_LONG
                            ).show()
                        }
                    }
                }
            }
        }
    }

    private fun getPermissionsRequest() =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
            viewLifecycleOwner.lifecycleScope.launch {
                repeatOnLifecycle(Lifecycle.State.STARTED) {
                    if (isAllPermissionsGranted(REQUIRED_PERMISSIONS)) {
                        viewModel.isPermissionsGranted.emit(true)
                    } else {
                        viewModel.isPermissionsGranted.emit(false)
                    }
                }
            }
        }

    override fun onStop() {
        super.onStop()
        try {

        } catch (exc: Throwable) {
            Log.e(TAG, "Error closing camera", exc)
        }
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()


    }

    companion object {

        private val TAG = CameraFragment::class.java.simpleName
        private val REQUIRED_PERMISSIONS = arrayOf(
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.POST_NOTIFICATIONS,
            Manifest.permission.CAMERA,
            Manifest.permission.FOREGROUND_SERVICE,

            )
        private const val DEFAULT_WIDTH = 1280
        private const val DEFAULT_HEIGHT = 720
    }
}