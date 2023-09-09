package com.example.photodetection

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.icu.text.SimpleDateFormat
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import androidx.exifinterface.media.ExifInterface
import com.example.photodetection.ui.theme.PhotoDetectionTheme
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.Face
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetector
import com.google.mlkit.vision.face.FaceDetectorOptions
import java.io.File
import java.util.Date
import java.util.Locale

class MainActivity : ComponentActivity() {

    companion object {
        const val IMG_CLICK = 1
        private const val SCALING_FACTOR = 10
        private const val TAG = "FACE_DETECT_TAG"
    }

    private var imagePath: String = ""

    private lateinit var detector: FaceDetector
    private lateinit var bitmap: MutableState<Bitmap?>
    private lateinit var croppedBitmap: MutableState<Bitmap?>


    private lateinit var permissionLauncher: ActivityResultLauncher<String>
    private lateinit var cameraLauncher: ActivityResultLauncher<Intent>


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            PhotoDetectionTheme() {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {

                    ImageScreen()

                }
            }
        }

        permissionLauncher =
            registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
                if (isGranted) {
                } else {
                    Toast.makeText(
                        this@MainActivity,
                        "Camera Permission Denied",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }

        cameraLauncher =
            registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
                if (result.resultCode == RESULT_OK) {
                    bitmap.value = processImageRotation(imagePath)

                } else {
                    Toast.makeText(this@MainActivity, "Camera closed", Toast.LENGTH_SHORT).show()
                }
            }

        val realTimeFDO = FaceDetectorOptions.Builder()
            .setContourMode(FaceDetectorOptions.CONTOUR_MODE_ALL)
            .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_ALL)
            .build()

        detector = FaceDetection.getClient(realTimeFDO)
    }


    @Composable
    fun ImageScreen(){
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())) {

            ClickImage()
            Spacer(modifier = Modifier.height(10.dp))
            Button(
                onClick = {
                    bitmap.value?.let { analyzePhoto(it) }
                },
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(Color.Blue),
                modifier = Modifier
                    .padding(top = 20.dp, start = 20.dp, end = 20.dp, bottom = 10.dp)
                    .fillMaxWidth(),
            ) {
                Text(
                    text = "Detect Face",
                    modifier = Modifier.padding(8.dp),
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    color = Color.White,
                )
            }

            Spacer(modifier = Modifier.height(10.dp))
            Box(
                Modifier
                    .fillMaxWidth()
                    .padding(start = 60.dp, end = 60.dp)
                    .height(250.dp)
                    .border(BorderStroke(1.dp, Color.Blue)),
                        contentAlignment = Alignment.Center
            ) {
                val bitmapValue = croppedBitmap.value

                if (bitmapValue == null) {

                    Image(
                        painter = painterResource(id = R.drawable.ic_launcher_foreground),
                        contentDescription = "",
                        modifier = Modifier
                            .size(90.dp)
                    )
                } else {
                    Image(bitmap = bitmapValue.asImageBitmap(), contentDescription = "")
                }

            }
        }
    }


    @Composable
    fun ClickImage() {

        Column(Modifier.fillMaxSize()) {
            val imgBitmap =
                if (imagePath.isEmpty()) {
                    null
                } else {
                    BitmapFactory.decodeFile(imagePath)
                }

            bitmap = remember { mutableStateOf(imgBitmap) }
            croppedBitmap = remember { mutableStateOf(null) }

            Box(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 10.dp)
                    .border(BorderStroke(1.dp, Color.Blue))
                    .height(220.dp)
                    .clickable {
                        onImageClick(IMG_CLICK)
                    },
                contentAlignment = Alignment.Center
            ) {

                val bitmapValue = bitmap.value

                if (bitmapValue == null) {

                    Image(
                        painter = painterResource(id = R.drawable.ic_launcher_foreground),
                        contentDescription = "",
                        modifier = Modifier
                            .size(90.dp)
                    )
                } else {
                    Image(bitmap = bitmapValue.asImageBitmap(), contentDescription = "")
                }
            }
        }
    }


    private fun analyzePhoto(bitmap: Bitmap) {
        Log.d(TAG, "analyzePhoto: w: ${bitmap.width} | h: ${bitmap.height}")

        val inputImage = InputImage.fromBitmap(bitmap, 0)

        detector.process(inputImage)
            .addOnSuccessListener { faces->
                Log.d(TAG, "analyzePhoto: Successfully Detected $faces")
                Toast.makeText(this, "Face Detected ${faces.size}", Toast.LENGTH_SHORT).show()

                if (faces.size < 1) {
                    return@addOnSuccessListener
                }
                val face = faces[0]
//                    val rect = face.boundingBox
//                    rect.set(
//                        rect.left * SCALING_FACTOR,
//                        rect.top * (SCALING_FACTOR - 1),
//                        rect.right * SCALING_FACTOR,
//                        rect.bottom * SCALING_FACTOR + 90
//                    )


                this.bitmap.value?.let { cropDetectedFace(it, face) }
            }
            .addOnFailureListener{ e->
                Log.e(TAG, "analyzePhoto: ", e)
                Toast.makeText(this, "Failed due to ${e.message}", Toast.LENGTH_SHORT).show()

            }
    }


    private fun cropDetectedFace(bitmap: Bitmap, face: Face) {
        Log.d(TAG, "cropDetectedFace: ")

        val rect = face.boundingBox
        Log.d(TAG, "rect: ${rect.left} | ${rect.top} | ${rect.right} | ${rect.bottom}")
        val x = Math.max(rect.left, 0)
        val y = Math.max(rect.top, 0)

        val width = rect.width()
        val height = rect.height()

        Log.d(TAG, "width: ${bitmap.width} $width")
        Log.d(TAG, "height: ${bitmap.height} $height")

        val croppedBitmap = Bitmap.createBitmap(
            bitmap, x, y,
            if (x + width > bitmap.width) bitmap.width - width else width,
            if (y + height > bitmap.height) bitmap.height - height else height
        )
        this.croppedBitmap.value = croppedBitmap
    }

    private fun onImageClick(imgClicked: Int) {
        if (checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            openCamera(imgClicked)
        } else {
            askCameraPermission()
        }
    }

    private fun askCameraPermission() {
        permissionLauncher.launch(Manifest.permission.CAMERA)
    }

    private fun openCamera(imgClicked: Int) {
        val intent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
        if (intent.resolveActivity(packageManager) != null) {
            val photoFile: File? = try {
                createPhotoFile()
            } catch (ex: Exception) {
                ex.printStackTrace()
                null
            }

            if (photoFile != null) {
                val uri = FileProvider.getUriForFile(
                    this@MainActivity,
                    "com.example.photodetection.provider",
                    photoFile
                )
                intent.putExtra(MediaStore.EXTRA_OUTPUT, uri)
                if (imgClicked == MainActivity.IMG_CLICK) {
                    imagePath = photoFile.absolutePath
                    cameraLauncher.launch(intent)
                }
            }
        } else {
            Toast.makeText(this@MainActivity, "No camera app found", Toast.LENGTH_SHORT).show()
        }
    }

    private fun createPhotoFile(): File {
        val fileTimestamp = SimpleDateFormat("yyyy-MM-dd_HHmm", Locale.US).format(Date())
        val fileName = "JPEG_$fileTimestamp"
        val storageDir = getExternalFilesDir(Environment.DIRECTORY_PICTURES)
        return File.createTempFile(fileName, ".jpg", storageDir)
    }

    private fun processImageRotation(photoPath: String): Bitmap? {
        val ei = ExifInterface(photoPath)
        val orientation: Int = ei.getAttributeInt(
            ExifInterface.TAG_ORIENTATION,
            ExifInterface.ORIENTATION_UNDEFINED
        )

        val bmp = BitmapFactory.decodeFile(photoPath)
        bmp ?: return null

        return when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> rotateImage(bmp, 90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> rotateImage(bmp, 180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> rotateImage(bmp, 270f)
            ExifInterface.ORIENTATION_NORMAL -> bmp
            else -> bmp
        }
    }

    private fun rotateImage(source: Bitmap, angle: Float): Bitmap? {
        val matrix = Matrix()
        matrix.postRotate(angle)
        return Bitmap.createBitmap(
            source, 0, 0, source.width, source.height,
            matrix, true
        )
    }
}
