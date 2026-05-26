package com.darkssh.client.terminal;

import android.os.Handler;
import android.os.Looper;
import android.os.Message;

import com.darkssh.client.terminal.emulator.TerminalEmulator;
import com.darkssh.client.terminal.emulator.TerminalSession;
import com.darkssh.client.terminal.emulator.TerminalSessionClient;

import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

import timber.log.Timber;

/**
 * A TerminalSession that uses SSH transport instead of a local PTY subprocess.
 */
public class DarkTerminalSession extends TerminalSession {

    private static final int MSG_NEW_INPUT = 1;

    private TerminalEmulator mEmulator;
    private final TerminalSessionClient mClient;
    private final int mTranscriptRows;
    private final Handler mMainThreadHandler = new MainThreadHandler();
    private boolean mIsRunning = true;
    private boolean mEmulatorInitialized = false;

    /** Buffer for incoming data before emulator is initialized. */
    private final List<byte[]> mPendingData = new ArrayList<>();

    /** Callback to receive keyboard input for SSH transport. */
    public interface KeyboardInputListener {
        void onKeyboardInput(byte[] data);
    }

    private KeyboardInputListener mKeyboardListener;

    public DarkTerminalSession(int transcriptRows, TerminalSessionClient client) {
        super("/bin/sh", "/", new String[0], new String[0], transcriptRows, client);
        this.mTranscriptRows = transcriptRows;
        this.mClient = client;
    }

    public void setKeyboardListener(KeyboardInputListener listener) {
        this.mKeyboardListener = listener;
    }

    @Override
    public void updateSize(int columns, int rows, int cellWidthPixels, int cellHeightPixels) {
        if (mEmulator == null) {
            Timber.d("DarkTerminalSession: Creating emulator %dx%d", columns, rows);
            mEmulator = new TerminalEmulator(this, columns, rows, cellWidthPixels, cellHeightPixels, mTranscriptRows, mClient);
            mEmulatorInitialized = true;

            synchronized (mPendingData) {
                for (byte[] data : mPendingData) {
                    mEmulator.append(data, data.length);
                }
                mPendingData.clear();
            }
            notifyScreenUpdate();
        } else {
            mEmulator.resize(columns, rows, cellWidthPixels, cellHeightPixels);
        }
    }

    @Override
    public TerminalEmulator getEmulator() {
        return mEmulator;
    }

    @Override
    public void write(byte[] data, int offset, int count) {
        if (!mIsRunning) return;
        byte[] buf = new byte[count];
        System.arraycopy(data, offset, buf, 0, count);
        if (mKeyboardListener != null) {
            mKeyboardListener.onKeyboardInput(buf);
        }
    }

    public void append(byte[] data, int len) {
        if (!mIsRunning) return;
        byte[] buf = new byte[len];
        System.arraycopy(data, 0, buf, 0, len);

        if (mEmulatorInitialized && mEmulator != null) {
            mEmulator.append(buf, len);
            notifyScreenUpdate();
        } else {
            synchronized (mPendingData) {
                mPendingData.add(buf);
                if (mPendingData.size() > 1000) {
                    mPendingData.remove(0);
                }
            }
        }
    }

    public void append(byte[] data) {
        append(data, data.length);
    }

    protected void notifyScreenUpdate() {
        mMainThreadHandler.sendEmptyMessage(MSG_NEW_INPUT);
    }

    public void finish() {
        mIsRunning = false;
    }

    @Override
    public String getTitle() {
        return (mEmulator == null) ? null : mEmulator.getTitle();
    }

    @Override
    public void reset() {
        if (mEmulator != null) {
            mEmulator.reset();
            notifyScreenUpdate();
        }
    }

    @Override
    public String getCwd() {
        return null;
    }

    @Override
    public int getExitStatus() {
        return -1;
    }

    protected void cleanupResources(int exitStatus) {
        // No-op for SSH sessions
    }

    class MainThreadHandler extends Handler {
        @Override
        public void handleMessage(@NotNull Message msg) {
            if (msg.what == MSG_NEW_INPUT) {
                mClient.onTextChanged(DarkTerminalSession.this);
            }
        }
    }
}
