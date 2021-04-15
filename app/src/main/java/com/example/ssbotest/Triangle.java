package com.example.ssbotest;

import android.opengl.GLES31;
import android.opengl.GLU;
import android.util.Log;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;

import static android.opengl.GLES31.GL_SHADER_STORAGE_BUFFER;

public class Triangle {

    // number of coordinates per vertex in this array
    static final int COORDS_PER_VERTEX = 3;
    static float[] triangleCoords = {   // in counterclockwise order:
            0.0f, 0.622008459f, 0.0f, // top
            -0.5f, -0.311004243f, 0.0f, // bottom left
            0.5f, -0.311004243f, 0.0f  // bottom right
    };
    private final String vertexShaderCode =
            "#version 310 es\n" +
                    "in vec4 vPosition;" +
                    "void main() {" +
                    "  gl_Position = vPosition;" +
                    "}";
    private final String fragmentShaderCode =
            "#version 310 es\n" +
                    "precision mediump float;\n" +
                    "layout(std430, binding = 0) readonly buffer layoutName \n" +
                    "{ \n" +
                    "  vec4 data[]; \n" +
                    "} testSsbo;\n" +
                    "uniform int uTestSSBOOffset;\n" +
                    "out vec4 outColor;\n" +
                    "void main() { \n" +
                    "  uint testOffset = uint(uTestSSBOOffset);\n" +
                    "  outColor = testSsbo.data[testOffset];\n" +
                    "}";
    private final int mProgram;
    private final int vertexCount = triangleCoords.length / COORDS_PER_VERTEX;
    private final int vertexStride = COORDS_PER_VERTEX * 4; // 4 bytes per vertex
    private final FloatBuffer vertexBuffer;
    private int positionHandle;
    // ssbo related members
    private int ssboOffsetHandle;
    private int ssboHandle;
    private int ssboSize;
    private int ssboOffsetIndex;


    public Triangle() {
        // initialize vertex byte buffer for shape coordinates
        ByteBuffer bb = ByteBuffer.allocateDirect(
                // (number of coordinate values * 4 bytes per float)
                triangleCoords.length * 4);
        // use the device hardware's native byte order
        bb.order(ByteOrder.nativeOrder());

        // create a floating point buffer from the ByteBuffer
        vertexBuffer = bb.asFloatBuffer();
        // add the coordinates to the FloatBuffer
        vertexBuffer.put(triangleCoords);
        // set the buffer to read the first coordinate
        vertexBuffer.position(0);

        int vertexShader = MyGLRenderer.loadShader(GLES31.GL_VERTEX_SHADER,
                vertexShaderCode);
        int fragmentShader = MyGLRenderer.loadShader(GLES31.GL_FRAGMENT_SHADER,
                fragmentShaderCode);

        // create empty OpenGL ES Program
        mProgram = GLES31.glCreateProgram();

        // add the vertex shader to program
        GLES31.glAttachShader(mProgram, vertexShader);

        // add the fragment shader to program
        GLES31.glAttachShader(mProgram, fragmentShader);

        // creates OpenGL ES program executables
        GLES31.glLinkProgram(mProgram);

        initSSBO();
    }

    public void draw() {
        // Add program to OpenGL ES environment
        GLES31.glUseProgram(mProgram);

        // get handle to vertex shader's vPosition member
        positionHandle = GLES31.glGetAttribLocation(mProgram, "vPosition");

        // Enable a handle to the triangle vertices
        GLES31.glEnableVertexAttribArray(positionHandle);

        // Prepare the triangle coordinate data
        GLES31.glVertexAttribPointer(positionHandle, COORDS_PER_VERTEX,
                GLES31.GL_FLOAT, false,
                vertexStride, vertexBuffer);

        ssboOffsetHandle = GLES31.glGetUniformLocation(mProgram, "uTestSSBOOffset");
        GLES31.glUniform1i(ssboOffsetHandle, ssboOffsetIndex);

        GLES31.glBindBufferRange(GL_SHADER_STORAGE_BUFFER, 0, ssboHandle, 0, ssboSize);
//        GLES31.glBindBufferBase(GL_SHADER_STORAGE_BUFFER, 0, ssboHandle);
        // Draw the triangle
        GLES31.glDrawArrays(GLES31.GL_TRIANGLES, 0, vertexCount);

        // Disable vertex array
        GLES31.glDisableVertexAttribArray(positionHandle);
        checkGL();
    }

    public void initSSBO() {
        // INCREASE THIS VALUE AND IT WILL NOT WORK ANYMORE (so everything from 2^15 on)
        // offset which represents dummy data that is not relevant for now
        ssboOffsetIndex = 1024 * 32 - 1;

        // offset * 4 because the index points to a vec4 in the shader
        int ssboOffset = ssboOffsetIndex * 4;

        // offset + 4 as we add the 4 relevant floats (== 1 vec4) and * 4 because we have 4 bytes per float
        FloatBuffer data = ByteBuffer.allocateDirect(
                (ssboOffset + 4) * 4)
                .order(ByteOrder.nativeOrder())
                .asFloatBuffer();

        data.put(ssboOffset + 0, 0.0f);
        data.put(ssboOffset + 1, 0.33f);
        data.put(ssboOffset + 2, 0.66f);
        data.put(ssboOffset + 3, 1.0f);

        data.position(0);

        // 4 byte per float
        ssboSize = data.capacity() * 4;

        int[] id = new int[1];
        GLES31.glGenBuffers(id.length, id, 0);
        GLES31.glBindBuffer(GL_SHADER_STORAGE_BUFFER, id[0]);

        GLES31.glBufferData(GL_SHADER_STORAGE_BUFFER, ssboSize, data, GLES31.GL_STATIC_COPY);
        checkGL();


        GLES31.glMemoryBarrier(GLES31.GL_ALL_BARRIER_BITS);

        GLES31.glBindBuffer(GL_SHADER_STORAGE_BUFFER, 0);
        ssboHandle = id[0];

        // max ssbo size is way above our limit
        printParam(GLES31.GL_MAX_SHADER_STORAGE_BLOCK_SIZE, "GL_MAX_SHADER_STORAGE_BLOCK_SIZE");
        printParam(GLES31.GL_MAX_SHADER_STORAGE_BUFFER_BINDINGS, "GL_MAX_SHADER_STORAGE_BUFFER_BINDINGS");
        printParam(GLES31.GL_MAX_COMBINED_SHADER_STORAGE_BLOCKS, "GL_MAX_COMBINED_SHADER_STORAGE_BLOCKS");
        printParam(GLES31.GL_MAX_COMPUTE_SHADER_STORAGE_BLOCKS, "GL_MAX_COMPUTE_SHADER_STORAGE_BLOCKS");
        printParam(GLES31.GL_MAX_FRAGMENT_SHADER_STORAGE_BLOCKS, "GL_MAX_FRAGMENT_SHADER_STORAGE_BLOCKS");
        printParam(GLES31.GL_MAX_VERTEX_SHADER_STORAGE_BLOCKS, "GL_MAX_VERTEX_SHADER_STORAGE_BLOCKS");
    }

    void printParam(int param, String name) {
        int[] params = new int[1];
        GLES31.glGetIntegerv(param, params, 0);
        Log.i("TAG", name + ": " + params[0]);
    }

    void checkGL() {
        int error;
        if ((error = GLES31.glGetError()) != GLES31.GL_NO_ERROR) {
            Log.e("TAG", "GL ERROR: " + GLU.gluErrorString(error));
        }
    }
}