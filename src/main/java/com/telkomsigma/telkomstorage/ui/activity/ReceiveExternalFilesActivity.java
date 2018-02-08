/**
 * ownCloud Android client application
 *
 * @author Bartek Przybylski
 * @author masensio
 * @author Juan Carlos González Cabrero
 * @author David A. Velasco
 * Copyright (C) 2012  Bartek Przybylski
 * Copyright (C) 2016 ownCloud Inc.
 * <p>
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License version 2,
 * as published by the Free Software Foundation.
 * <p>
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * <p>
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.telkomsigma.telkomstorage.ui.activity;

import android.accounts.Account;
import android.accounts.AccountManager;
import android.accounts.AuthenticatorException;
import android.app.Activity;
import android.app.Dialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnClickListener;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Resources.NotFoundException;
import android.graphics.PorterDuff;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Parcelable;
import android.support.annotation.DrawableRes;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.annotation.StringRes;
import android.support.v4.app.DialogFragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.content.ContextCompat;
import android.support.v4.graphics.drawable.DrawableCompat;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AlertDialog.Builder;
import android.text.format.DateFormat;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.WindowManager.LayoutParams;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.Spinner;
import android.widget.TextView;

import com.owncloud.android.lib.common.operations.RemoteOperation;
import com.owncloud.android.lib.common.operations.RemoteOperationResult;
import com.owncloud.android.lib.common.operations.RemoteOperationResult.ResultCode;
import com.owncloud.android.lib.common.utils.Log_OC;
import com.telkomsigma.telkomstorage.MainApp;
import com.telkomsigma.telkomstorage.datamodel.OCFile;
import com.telkomsigma.telkomstorage.db.PreferenceManager;
import com.telkomsigma.telkomstorage.files.services.FileUploader;
import com.telkomsigma.telkomstorage.operations.CreateFolderOperation;
import com.telkomsigma.telkomstorage.operations.RefreshFolderOperation;
import com.telkomsigma.telkomstorage.operations.UploadFileOperation;
import com.telkomsigma.telkomstorage.syncadapter.FileSyncAdapter;
import com.telkomsigma.telkomstorage.ui.adapter.AccountListAdapter;
import com.telkomsigma.telkomstorage.ui.adapter.AccountListItem;
import com.telkomsigma.telkomstorage.ui.adapter.UploaderAdapter;
import com.telkomsigma.telkomstorage.ui.asynctasks.CopyAndUploadContentUrisTask;
import com.telkomsigma.telkomstorage.ui.dialog.ConfirmationDialogFragment;
import com.telkomsigma.telkomstorage.ui.dialog.CreateFolderDialogFragment;
import com.telkomsigma.telkomstorage.ui.fragment.TaskRetainerFragment;
import com.telkomsigma.telkomstorage.ui.helpers.UriUploader;
import com.telkomsigma.telkomstorage.utils.DataHolderUtil;
import com.telkomsigma.telkomstorage.utils.DisplayUtils;
import com.telkomsigma.telkomstorage.utils.ErrorMessageAdapter;
import com.telkomsigma.telkomstorage.utils.FileStorageUtils;
import com.telkomsigma.telkomstorage.utils.ThemeUtils;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Stack;
import java.util.Vector;


/**
 * This can be used to upload things to an ownCloud instance.
 */
public class ReceiveExternalFilesActivity extends FileActivity
        implements OnItemClickListener, View.OnClickListener, CopyAndUploadContentUrisTask.OnCopyTmpFilesTaskListener {

    public static final String TEXT_FILE_SUFFIX = ".txt";
    public static final String URL_FILE_SUFFIX = ".url";
    public static final String WEBLOC_FILE_SUFFIX = ".webloc";
    public static final String DESKTOP_FILE_SUFFIX = ".desktop";
    private static final String TAG = ReceiveExternalFilesActivity.class.getSimpleName();
    private static final String FTAG_ERROR_FRAGMENT = "ERROR_FRAGMENT";
    private final static int REQUEST_CODE__SETUP_ACCOUNT = REQUEST_CODE__LAST_SHARED + 1;
    private final static String KEY_PARENTS = "PARENTS";
    private final static String KEY_FILE = "FILE";
    private final static String FILENAME_ENCODING = "UTF-8";
    private AccountManager mAccountManager;
    private Stack<String> mParents;
    private ArrayList<Parcelable> mStreamsToUpload;
    private String mUploadPath;
    private OCFile mFile;
    private SyncBroadcastReceiver mSyncBroadcastReceiver;
    private boolean mSyncInProgress = false;
    private boolean mUploadFromTmpFile = false;
    private String mSubjectText;
    private String mExtraText;
    private LinearLayout mEmptyListContainer;
    private TextView mEmptyListMessage;
    private TextView mEmptyListHeadline;
    private ImageView mEmptyListIcon;
    private ProgressBar mEmptyListProgress;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        prepareStreamsToUpload();

        if (savedInstanceState == null) {
            mParents = new Stack<>();
        } else {
            mParents = (Stack<String>) savedInstanceState.getSerializable(KEY_PARENTS);
            mFile = savedInstanceState.getParcelable(KEY_FILE);
        }

        super.onCreate(savedInstanceState);

        // Listen for sync messages
        IntentFilter syncIntentFilter = new IntentFilter(RefreshFolderOperation.
                EVENT_SINGLE_FOLDER_CONTENTS_SYNCED);
        syncIntentFilter.addAction(RefreshFolderOperation.EVENT_SINGLE_FOLDER_SHARES_SYNCED);
        mSyncBroadcastReceiver = new SyncBroadcastReceiver();
        registerReceiver(mSyncBroadcastReceiver, syncIntentFilter);

        // Init Fragment without UI to retain AsyncTask across configuration changes
        FragmentManager fm = getSupportFragmentManager();
        TaskRetainerFragment taskRetainerFragment =
                (TaskRetainerFragment) fm.findFragmentByTag(TaskRetainerFragment.FTAG_TASK_RETAINER_FRAGMENT);
        if (taskRetainerFragment == null) {
            taskRetainerFragment = new TaskRetainerFragment();
            fm.beginTransaction()
                    .add(taskRetainerFragment, TaskRetainerFragment.FTAG_TASK_RETAINER_FRAGMENT).commit();
        }   // else, Fragment already created and retained across configuration change
    }

    @Override
    protected void setAccount(Account account, boolean savedAccount) {
        if (somethingToUpload()) {
            mAccountManager = (AccountManager) getSystemService(ACCOUNT_SERVICE);
            Account[] accounts = mAccountManager.getAccountsByType(MainApp.getAccountType());
            if (accounts.length == 0) {
                Log_OC.i(TAG, "No telkomStorage account is available");
                DialogNoAccount dialog = new DialogNoAccount();
                dialog.show(getSupportFragmentManager(), null);
            } else {
                if (!savedAccount) {
                    setAccount(accounts[0]);
                }
            }
        } else {
            showErrorDialog(
                    com.telkomsigma.telkomstorage.R.string.uploader_error_message_no_file_to_upload,
                    com.telkomsigma.telkomstorage.R.string.uploader_error_title_no_file_to_upload
            );
        }

        super.setAccount(account, savedAccount);
    }

    private void showAccountChooserDialog() {
        DialogMultipleAccount dialog = new DialogMultipleAccount();
        dialog.show(getSupportFragmentManager(), null);
    }

    private Activity getActivity() {
        return this;
    }

    @Override
    protected void onAccountSet(boolean stateWasRecovered) {
        super.onAccountSet(mAccountWasRestored);
        initTargetFolder();
        populateDirectoryList();
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        Log_OC.d(TAG, "onSaveInstanceState() start");
        super.onSaveInstanceState(outState);
        outState.putSerializable(KEY_PARENTS, mParents);
        outState.putParcelable(KEY_FILE, mFile);
        outState.putParcelable(FileActivity.EXTRA_ACCOUNT, getAccount());

        Log_OC.d(TAG, "onSaveInstanceState() end");
    }

    @Override
    protected void onDestroy() {
        if (mSyncBroadcastReceiver != null) {
            unregisterReceiver(mSyncBroadcastReceiver);
        }
        super.onDestroy();
    }

    @Override
    public void onBackPressed() {
        if (mParents.size() <= 1) {
            super.onBackPressed();
        } else {
            mParents.pop();
            String full_path = generatePath(mParents);
            startSyncFolderOperation(getStorageManager().getFileByPath(full_path));
            populateDirectoryList();
        }
    }

    @Override
    public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
        // click on folder in the list
        Log_OC.d(TAG, "on item click");
        Vector<OCFile> tmpfiles = getStorageManager().getFolderContent(mFile, false);
        sortFileList(tmpfiles);

        if (tmpfiles.size() <= 0) {
            return;
        }
        // filter on dirtype
        Vector<OCFile> files = new Vector<>();
        for (OCFile f : tmpfiles) {
            files.add(f);
        }
        if (files.size() < position) {
            throw new IndexOutOfBoundsException("Incorrect item selected");
        }
        if (files.get(position).isFolder()) {
            OCFile folderToEnter = files.get(position);
            startSyncFolderOperation(folderToEnter);
            mParents.push(folderToEnter.getFileName());
            populateDirectoryList();
        }
    }

    @Override
    public void onClick(View v) {
        // click on button
        switch (v.getId()) {
            case com.telkomsigma.telkomstorage.R.id.uploader_choose_folder:
                mUploadPath = "";   // first element in mParents is root dir, represented by "";
                // init mUploadPath with "/" results in a "//" prefix
                for (String p : mParents) {
                    mUploadPath += p + OCFile.PATH_SEPARATOR;
                }

                if (mUploadFromTmpFile) {
                    DialogInputUploadFilename dialog = DialogInputUploadFilename.newInstance(mSubjectText, mExtraText);
                    dialog.show(getSupportFragmentManager(), null);
                } else {
                    Log_OC.d(TAG, "Uploading file to dir " + mUploadPath);
                    uploadFiles();
                }
                break;

            case com.telkomsigma.telkomstorage.R.id.uploader_cancel:
                finish();
                break;

            default:
                throw new IllegalArgumentException("Wrong element clicked");
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        Log_OC.i(TAG, "result received. req: " + requestCode + " res: " + resultCode);
        if (requestCode == REQUEST_CODE__SETUP_ACCOUNT) {
            if (resultCode == RESULT_CANCELED) {
                finish();
            }
            Account[] accounts = mAccountManager.getAccountsByType(MainApp.getAuthTokenType());
            if (accounts.length == 0) {
                DialogNoAccount dialog = new DialogNoAccount();
                dialog.show(getSupportFragmentManager(), null);
            } else {
                // there is no need for checking for is there more then one
                // account at this point
                // since account setup can set only one account at time
                setAccount(accounts[0]);
                populateDirectoryList();
            }
        }
    }

    private void setupActionBarSubtitle() {
        if (isHaveMultipleAccount()) {
            ActionBar actionBar = getSupportActionBar();
            if (actionBar != null) {
                actionBar.setSubtitle(getAccount().name);
            }
        }
    }

    private void populateDirectoryList() {
        setContentView(com.telkomsigma.telkomstorage.R.layout.uploader_layout);
        setupEmptyList();
        setupToolbar();
        ActionBar actionBar = getSupportActionBar();
        setupActionBarSubtitle();

        ListView mListView = findViewById(android.R.id.list);

        String current_dir = mParents.peek();
        if ("".equals(current_dir)) {
            actionBar.setTitle(getString(com.telkomsigma.telkomstorage.R.string.uploader_top_message));
        } else {
            actionBar.setTitle(current_dir);
        }

        boolean notRoot = (mParents.size() > 1);

        actionBar.setDisplayHomeAsUpEnabled(notRoot);
        actionBar.setHomeButtonEnabled(notRoot);

        String full_path = generatePath(mParents);

        Log_OC.d(TAG, "Populating view with content of : " + full_path);

        mFile = getStorageManager().getFileByPath(full_path);
        if (mFile != null) {
            Vector<OCFile> files = getStorageManager().getFolderContent(mFile, false);

            if (files.size() == 0) {
                setMessageForEmptyList(
                        com.telkomsigma.telkomstorage.R.string.file_list_empty_headline,
                        com.telkomsigma.telkomstorage.R.string.empty,
                        com.telkomsigma.telkomstorage.R.drawable.ic_list_empty_upload,
                        true
                );
            } else {
                mEmptyListContainer.setVisibility(View.GONE);

                sortFileList(files);

                List<HashMap<String, Object>> data = new LinkedList<>();
                for (OCFile f : files) {
                    HashMap<String, Object> h = new HashMap<>();
                    h.put("dirname", f);
                    data.add(h);
                }

                UploaderAdapter sa = new UploaderAdapter(this,
                        data,
                        com.telkomsigma.telkomstorage.R.layout.uploader_list_item_layout,
                        new String[]{"dirname"},
                        new int[]{com.telkomsigma.telkomstorage.R.id.filename},
                        getStorageManager(), getAccount());

                mListView.setAdapter(sa);
            }
            Button btnChooseFolder = findViewById(com.telkomsigma.telkomstorage.R.id.uploader_choose_folder);
            btnChooseFolder.setOnClickListener(this);
            btnChooseFolder.getBackground().setColorFilter(ThemeUtils.primaryColor(getAccount()),
                    PorterDuff.Mode.SRC_ATOP);

            if (getSupportActionBar() != null) {
                getSupportActionBar().setBackgroundDrawable(new ColorDrawable(
                        ThemeUtils.primaryColor(getAccount())));
            }

            ThemeUtils.colorStatusBar(this, ThemeUtils.primaryDarkColor(getAccount()));

            ThemeUtils.colorToolbarProgressBar(this, ThemeUtils.primaryColor(getAccount()));

            Button btnNewFolder = findViewById(com.telkomsigma.telkomstorage.R.id.uploader_cancel);
            btnNewFolder.setOnClickListener(this);

            mListView.setOnItemClickListener(this);
        }
    }

    protected void setupEmptyList() {
        mEmptyListContainer = findViewById(com.telkomsigma.telkomstorage.R.id.empty_list_view);
        mEmptyListMessage = findViewById(com.telkomsigma.telkomstorage.R.id.empty_list_view_text);
        mEmptyListHeadline = findViewById(com.telkomsigma.telkomstorage.R.id.empty_list_view_headline);
        mEmptyListIcon = findViewById(com.telkomsigma.telkomstorage.R.id.empty_list_icon);
        mEmptyListProgress = findViewById(com.telkomsigma.telkomstorage.R.id.empty_list_progress);
        mEmptyListProgress.getIndeterminateDrawable().setColorFilter(ThemeUtils.primaryColor(),
                PorterDuff.Mode.SRC_IN);
    }

    public void setMessageForEmptyList(@StringRes final int headline, @StringRes final int message,
                                       @DrawableRes final int icon, final boolean tintIcon) {
        new Handler(Looper.getMainLooper()).post(new Runnable() {
            @Override
            public void run() {

                if (mEmptyListContainer != null && mEmptyListMessage != null) {
                    mEmptyListHeadline.setText(headline);
                    mEmptyListMessage.setText(message);

                    if (tintIcon) {
                        mEmptyListIcon.setImageDrawable(ThemeUtils.tintDrawable(icon, ThemeUtils.primaryColor()));
                    }

                    mEmptyListIcon.setVisibility(View.VISIBLE);
                    mEmptyListProgress.setVisibility(View.GONE);
                    mEmptyListMessage.setVisibility(View.VISIBLE);
                }
            }
        });
    }

    @Override
    public void onSavedCertificate() {
        startSyncFolderOperation(getCurrentDir());
    }

    private void startSyncFolderOperation(OCFile folder) {
        long currentSyncTime = System.currentTimeMillis();

        mSyncInProgress = true;

        // perform folder synchronization
        RemoteOperation synchFolderOp = new RefreshFolderOperation(folder,
                currentSyncTime,
                false,
                false,
                false,
                getStorageManager(),
                getAccount(),
                getApplicationContext()
        );
        synchFolderOp.execute(getAccount(), this, null, null);
    }

    private Vector<OCFile> sortFileList(Vector<OCFile> files) {
        // Read sorting order, default to sort by name ascending
        FileStorageUtils.mSortOrder = PreferenceManager.getSortOrder(this);
        FileStorageUtils.mSortAscending = PreferenceManager.getSortAscending(this);
        return FileStorageUtils.sortOcFolder(files);
    }

    private String generatePath(Stack<String> dirs) {
        String full_path = "";

        for (String a : dirs) {
            full_path += a + "/";
        }
        return full_path;
    }

    private void prepareStreamsToUpload() {
        Intent intent = getIntent();
        if (intent.getAction().equals(Intent.ACTION_SEND)) {
            mStreamsToUpload = new ArrayList<>();
            mStreamsToUpload.add(intent.getParcelableExtra(Intent.EXTRA_STREAM));
        } else if (intent.getAction().equals(Intent.ACTION_SEND_MULTIPLE)) {
            mStreamsToUpload = intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM);
        }

        if (mStreamsToUpload == null || mStreamsToUpload.get(0) == null) {
            mStreamsToUpload = null;
            saveTextsFromIntent(intent);
        }
    }

    private void saveTextsFromIntent(Intent intent) {
        if (!intent.getType().equals("text/plain")) {
            return;
        }
        mUploadFromTmpFile = true;

        mSubjectText = intent.getStringExtra(Intent.EXTRA_SUBJECT);
        if (mSubjectText == null) {
            mSubjectText = intent.getStringExtra(Intent.EXTRA_TITLE);
            if (mSubjectText == null) {
                mSubjectText = DateFormat.format("yyyyMMdd_kkmmss", Calendar.getInstance()).toString();
            }
        }
        mExtraText = intent.getStringExtra(Intent.EXTRA_TEXT);
    }

    private boolean somethingToUpload() {
        return (mStreamsToUpload != null && mStreamsToUpload.size() > 0 && mStreamsToUpload.get(0) != null ||
                mUploadFromTmpFile);
    }

    public void uploadFile(String tmpname, String filename) {
        FileUploader.UploadRequester requester = new FileUploader.UploadRequester();
        requester.uploadNewFile(
                getBaseContext(),
                getAccount(),
                tmpname,
                mFile.getRemotePath() + filename,
                FileUploader.LOCAL_BEHAVIOUR_COPY,
                null,
                true,
                UploadFileOperation.CREATED_BY_USER,
                false,
                false
        );
        finish();
    }

    public void uploadFiles() {

        UriUploader uploader = new UriUploader(
                this,
                mStreamsToUpload,
                mUploadPath,
                getAccount(),
                FileUploader.LOCAL_BEHAVIOUR_FORGET,
                true, // Show waiting dialog while file is being copied from private storage
                this  // Copy temp task listener
        );

        UriUploader.UriUploaderResultCode resultCode = uploader.uploadUris();

        // Save the path to shared preferences; even if upload is not possible, user chose the folder
        PreferenceManager.setLastUploadPath(this, mUploadPath);

        if (resultCode == UriUploader.UriUploaderResultCode.OK) {
            finish();
        } else {

            int messageResTitle = com.telkomsigma.telkomstorage.R.string.uploader_error_title_file_cannot_be_uploaded;
            int messageResId = com.telkomsigma.telkomstorage.R.string.common_error_unknown;

            if (resultCode == UriUploader.UriUploaderResultCode.ERROR_NO_FILE_TO_UPLOAD) {
                messageResId = com.telkomsigma.telkomstorage.R.string.uploader_error_message_no_file_to_upload;
                messageResTitle = com.telkomsigma.telkomstorage.R.string.uploader_error_title_no_file_to_upload;
            } else if (resultCode == UriUploader.UriUploaderResultCode.ERROR_READ_PERMISSION_NOT_GRANTED) {
                messageResId = com.telkomsigma.telkomstorage.R.string.uploader_error_message_read_permission_not_granted;
            } else if (resultCode == UriUploader.UriUploaderResultCode.ERROR_UNKNOWN) {
                messageResId = com.telkomsigma.telkomstorage.R.string.common_error_unknown;
            }

            showErrorDialog(
                    messageResId,
                    messageResTitle
            );
        }
    }

    @Override
    public void onRemoteOperationFinish(RemoteOperation operation, RemoteOperationResult result) {
        super.onRemoteOperationFinish(operation, result);


        if (operation instanceof CreateFolderOperation) {
            onCreateFolderOperationFinish((CreateFolderOperation) operation, result);
        }

    }

    /**
     * Updates the view associated to the activity after the finish of an operation
     * trying create a new folder
     *
     * @param operation Creation operation performed.
     * @param result    Result of the creation.
     */
    private void onCreateFolderOperationFinish(CreateFolderOperation operation,
                                               RemoteOperationResult result) {
        if (result.isSuccess()) {
            String remotePath = operation.getRemotePath().substring(0, operation.getRemotePath().length() - 1);
            String newFolder = remotePath.substring(remotePath.lastIndexOf('/') + 1);
            mParents.push(newFolder);
            populateDirectoryList();
        } else {
            try {
                DisplayUtils.showSnackMessage(
                        this, ErrorMessageAdapter.getErrorCauseMessage(result, operation, getResources())
                );

            } catch (NotFoundException e) {
                Log_OC.e(TAG, "Error while trying to show fail message ", e);
            }
        }
    }

    /**
     * Loads the target folder initialize shown to the user.
     * <p/>
     * The target account has to be chosen before this method is called.
     */
    private void initTargetFolder() {
        if (getStorageManager() == null) {
            throw new IllegalStateException("Do not call this method before " +
                    "initializing mStorageManager");
        }

        String lastPath = PreferenceManager.getLastUploadPath(this);
        // "/" equals root-directory
        if ("/".equals(lastPath)) {
            mParents.add("");
        } else {
            String[] dir_names = lastPath.split("/");
            mParents.clear();
            for (String dir : dir_names) {
                mParents.add(dir);
            }
        }
        //Make sure that path still exists, if it doesn't pop the stack and try the previous path
        while (!getStorageManager().fileExists(generatePath(mParents)) && mParents.size() > 1) {
            mParents.pop();
        }
    }

    private boolean isHaveMultipleAccount() {
        return mAccountManager.getAccountsByType(MainApp.getAccountType()).length > 1;
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(com.telkomsigma.telkomstorage.R.menu.receive_file_menu, menu);

        if (!isHaveMultipleAccount()) {
            MenuItem switchAccountMenu = menu.findItem(com.telkomsigma.telkomstorage.R.id.action_switch_account);
            switchAccountMenu.setVisible(false);
        }

        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        boolean retval = true;
        switch (item.getItemId()) {
            case com.telkomsigma.telkomstorage.R.id.action_create_dir:
                CreateFolderDialogFragment dialog = CreateFolderDialogFragment.newInstance(mFile);
                dialog.show(
                        getSupportFragmentManager(),
                        CreateFolderDialogFragment.CREATE_FOLDER_FRAGMENT);
                break;
            case android.R.id.home:
                if ((mParents.size() > 1)) {
                    onBackPressed();
                }
                break;
            case com.telkomsigma.telkomstorage.R.id.action_switch_account:
                showAccountChooserDialog();
                break;

            default:
                retval = super.onOptionsItemSelected(item);
                break;
        }
        return retval;
    }

    private OCFile getCurrentFolder() {
        OCFile file = mFile;
        if (file != null) {
            if (file.isFolder()) {
                return file;
            } else if (getStorageManager() != null) {
                return getStorageManager().getFileByPath(file.getParentRemotePath());
            }
        }
        return null;
    }

    private void browseToRoot() {
        OCFile root = getStorageManager().getFileByPath(OCFile.ROOT_PATH);
        mFile = root;
        startSyncFolderOperation(root);
    }

    /**
     * Process the result of CopyAndUploadContentUrisTask
     */
    @Override
    public void onTmpFilesCopied(ResultCode result) {
        dismissLoadingDialog();
        finish();
    }

    /**
     * Show an error dialog, forcing the user to click a single button to exit the activity
     *
     * @param messageResId      Resource id of the message to show in the dialog.
     * @param messageResTitle   Resource id of the title to show in the dialog. 0 to show default alert message.
     *                          -1 to show no title.
     */
    private void showErrorDialog(int messageResId, int messageResTitle) {

        ConfirmationDialogFragment errorDialog = ConfirmationDialogFragment.newInstance(
                messageResId,
                new String[]{getString(com.telkomsigma.telkomstorage.R.string.app_name)}, // see uploader_error_message_* in strings.xml
                messageResTitle,
                com.telkomsigma.telkomstorage.R.string.common_back,
                -1,
                -1
        );
        errorDialog.setCancelable(false);
        errorDialog.setOnConfirmationListener(
                new ConfirmationDialogFragment.ConfirmationDialogFragmentListener() {
                    @Override
                    public void onConfirmation(String callerTag) {
                        finish();
                    }

                    @Override
                    public void onNeutral(String callerTag) {
                        // not used at the moment
                    }

                    @Override
                    public void onCancel(String callerTag) {
                        // not used at the moment
                    }
                }
        );
        errorDialog.show(getSupportFragmentManager(), FTAG_ERROR_FRAGMENT);
    }

    public static class DialogNoAccount extends DialogFragment {
        @Override
        public Dialog onCreateDialog(Bundle savedInstanceState) {
            AlertDialog.Builder builder = new Builder(getActivity());
            builder.setIcon(com.telkomsigma.telkomstorage.R.drawable.ic_warning);
            builder.setTitle(com.telkomsigma.telkomstorage.R.string.uploader_wrn_no_account_title);
            builder.setMessage(String.format(
                    getString(com.telkomsigma.telkomstorage.R.string.uploader_wrn_no_account_text),
                    getString(com.telkomsigma.telkomstorage.R.string.app_name)));
            builder.setCancelable(false);
            builder.setPositiveButton(com.telkomsigma.telkomstorage.R.string.uploader_wrn_no_account_setup_btn_text, new OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    // using string value since in API7 this
                    // constant is not defined
                    // in API7 < this constant is defined in
                    // Settings.ADD_ACCOUNT_SETTINGS
                    // and Settings.EXTRA_AUTHORITIES
                    Intent intent = new Intent(android.provider.Settings.ACTION_ADD_ACCOUNT);
                    intent.putExtra("authorities", new String[]{MainApp.getAuthTokenType()});
                    startActivityForResult(intent, REQUEST_CODE__SETUP_ACCOUNT);
                }
            });
            builder.setNegativeButton(com.telkomsigma.telkomstorage.R.string.uploader_wrn_no_account_quit_btn_text, new OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    getActivity().finish();
                }
            });
            return builder.create();
        }
    }

    public static class DialogMultipleAccount extends DialogFragment {
        private AccountListAdapter mAccountListAdapter;
        private Drawable mTintedCheck;

        @NonNull
        @Override
        public Dialog onCreateDialog(Bundle savedInstanceState) {
            final ReceiveExternalFilesActivity parent = (ReceiveExternalFilesActivity) getActivity();
            AlertDialog.Builder builder = new Builder(parent);

            mTintedCheck = DrawableCompat.wrap(ContextCompat.getDrawable(parent,
                    com.telkomsigma.telkomstorage.R.drawable.ic_account_circle_white_18dp));
            int tint = ThemeUtils.primaryColor();
            DrawableCompat.setTint(mTintedCheck, tint);

            mAccountListAdapter = new AccountListAdapter(parent, getAccountListItems(parent), mTintedCheck);

            builder.setTitle(com.telkomsigma.telkomstorage.R.string.common_choose_account);
            builder.setAdapter(mAccountListAdapter, new OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    final ReceiveExternalFilesActivity parent = (ReceiveExternalFilesActivity) getActivity();
                    parent.setAccount(parent.mAccountManager.getAccountsByType(MainApp.getAccountType())[which], false);
                    parent.onAccountSet(parent.mAccountWasRestored);
                    dialog.dismiss();
                }
            });
            builder.setCancelable(true);
            return builder.create();
        }

        /**
         * creates the account list items list including the add-account action in case multiaccount_support is enabled.
         *
         * @return list of account list items
         */
        private ArrayList<AccountListItem> getAccountListItems(ReceiveExternalFilesActivity activity) {
            Account[] accountList = activity.mAccountManager.getAccountsByType(MainApp.getAccountType());
            ArrayList<AccountListItem> adapterAccountList = new ArrayList<>(accountList.length);
            for (Account account : accountList) {
                adapterAccountList.add(new AccountListItem(account));
            }

            return adapterAccountList;
        }
    }

    public static class DialogInputUploadFilename extends DialogFragment {
        private static final String KEY_SUBJECT_TEXT = "SUBJECT_TEXT";
        private static final String KEY_EXTRA_TEXT = "EXTRA_TEXT";

        private static final int CATEGORY_URL = 1;
        private static final int CATEGORY_MAPS_URL = 2;

        private List<String> mFilenameBase;
        private List<String> mFilenameSuffix;
        private List<String> mText;
        private int mFileCategory;

        private Spinner mSpinner;

        public static DialogInputUploadFilename newInstance(String subjectText, String extraText) {
            DialogInputUploadFilename dialog = new DialogInputUploadFilename();
            Bundle args = new Bundle();
            args.putString(KEY_SUBJECT_TEXT, subjectText);
            args.putString(KEY_EXTRA_TEXT, extraText);
            dialog.setArguments(args);
            return dialog;
        }

        @NonNull
        @Override
        public Dialog onCreateDialog(Bundle savedInstanceState) {
            mFilenameBase = new ArrayList<>();
            mFilenameSuffix = new ArrayList<>();
            mText = new ArrayList<>();

            String subjectText = getArguments().getString(KEY_SUBJECT_TEXT);
            String extraText = getArguments().getString(KEY_EXTRA_TEXT);

            LayoutInflater layout = LayoutInflater.from(getActivity().getBaseContext());
            View view = layout.inflate(com.telkomsigma.telkomstorage.R.layout.upload_file_dialog, null);

            ArrayAdapter<String> adapter
                    = new ArrayAdapter<>(getActivity().getBaseContext(), android.R.layout.simple_spinner_item);
            adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);

            int selectPos = 0;
            String filename = renameSafeFilename(subjectText);
            if (filename == null) {
                filename = "";
            }
            adapter.add(getString(com.telkomsigma.telkomstorage.R.string.upload_file_dialog_filetype_snippet_text));
            mText.add(extraText);
            mFilenameBase.add(filename);
            mFilenameSuffix.add(TEXT_FILE_SUFFIX);
            if (isIntentStartWithUrl(extraText)) {
                String str = getString(com.telkomsigma.telkomstorage.R.string.upload_file_dialog_filetype_internet_shortcut);
                mText.add(internetShortcutUrlText(extraText));
                mFilenameBase.add(filename);
                mFilenameSuffix.add(URL_FILE_SUFFIX);
                adapter.add(String.format(str, URL_FILE_SUFFIX));

                mText.add(internetShortcutWeblocText(extraText));
                mFilenameBase.add(filename);
                mFilenameSuffix.add(WEBLOC_FILE_SUFFIX);
                adapter.add(String.format(str, WEBLOC_FILE_SUFFIX));

                mText.add(internetShortcutDesktopText(extraText, filename));
                mFilenameBase.add(filename);
                mFilenameSuffix.add(DESKTOP_FILE_SUFFIX);
                adapter.add(String.format(str, DESKTOP_FILE_SUFFIX));

                selectPos = PreferenceManager.getUploadUrlFileExtensionUrlSelectedPos(getActivity());
                mFileCategory = CATEGORY_URL;
            } else if (isIntentFromGoogleMap(subjectText, extraText)) {
                String str = getString(com.telkomsigma.telkomstorage.R.string.upload_file_dialog_filetype_googlemap_shortcut);
                String texts[] = extraText.split("\n");
                mText.add(internetShortcutUrlText(texts[2]));
                mFilenameBase.add(texts[0]);
                mFilenameSuffix.add(URL_FILE_SUFFIX);
                adapter.add(String.format(str, URL_FILE_SUFFIX));

                mText.add(internetShortcutWeblocText(texts[2]));
                mFilenameBase.add(texts[0]);
                mFilenameSuffix.add(WEBLOC_FILE_SUFFIX);
                adapter.add(String.format(str, WEBLOC_FILE_SUFFIX));

                mText.add(internetShortcutDesktopText(texts[2], texts[0]));
                mFilenameBase.add(texts[0]);
                mFilenameSuffix.add(DESKTOP_FILE_SUFFIX);
                adapter.add(String.format(str, DESKTOP_FILE_SUFFIX));

                selectPos = PreferenceManager.getUploadMapFileExtensionUrlSelectedPos(getActivity());
                mFileCategory = CATEGORY_MAPS_URL;
            }

            final EditText userInput = view.findViewById(com.telkomsigma.telkomstorage.R.id.user_input);
            setFilename(userInput, selectPos);
            userInput.requestFocus();

            final Spinner spinner = view.findViewById(com.telkomsigma.telkomstorage.R.id.file_type);
            setupSpinner(adapter, selectPos, userInput, spinner);
            if (adapter.getCount() == 1) {
                TextView label = view.findViewById(com.telkomsigma.telkomstorage.R.id.label_file_type);
                label.setVisibility(View.GONE);
                spinner.setVisibility(View.GONE);
            }
            mSpinner = spinner;

            Dialog filenameDialog = createFilenameDialog(view, userInput, spinner);
            filenameDialog.getWindow().setSoftInputMode(LayoutParams.SOFT_INPUT_STATE_VISIBLE);
            return filenameDialog;
        }

        private void setupSpinner(ArrayAdapter<String> adapter, int selectPos, final EditText userInput, Spinner spinner) {
            spinner.setAdapter(adapter);
            spinner.setSelection(selectPos, false);
            spinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
                @Override
                public void onItemSelected(AdapterView parent, View view, int position, long id) {
                    Spinner spinner = (Spinner) parent;
                    int selectPos = spinner.getSelectedItemPosition();
                    setFilename(userInput, selectPos);
                    saveSelection(selectPos);
                }

                @Override
                public void onNothingSelected(AdapterView<?> parent) {
                    // nothing to do
                }
            });
        }

        @NonNull
        private Dialog createFilenameDialog(View view, final EditText userInput, final Spinner spinner) {
            Builder builder = new Builder(getActivity());
            builder.setView(view);
            builder.setTitle(com.telkomsigma.telkomstorage.R.string.upload_file_dialog_title);
            builder.setPositiveButton(com.telkomsigma.telkomstorage.R.string.common_ok, new OnClickListener() {
                public void onClick(DialogInterface dialog, int id) {
                    int selectPos = spinner.getSelectedItemPosition();

                    // verify if file name has suffix
                    String filename = userInput.getText().toString();
                    String suffix = mFilenameSuffix.get(selectPos);
                    if (!filename.endsWith(suffix)) {
                        filename += suffix;
                    }

                    File file = createTempFile("tmp.tmp", mText.get(selectPos));
                    if (file == null) {
                        getActivity().finish();
                    }
                    String tmpname = file.getAbsolutePath();

                    ((ReceiveExternalFilesActivity) getActivity()).uploadFile(tmpname, filename);
                }
            });
            builder.setNegativeButton(com.telkomsigma.telkomstorage.R.string.common_cancel, new OnClickListener() {
                public void onClick(DialogInterface dialog, int id) {
                    dialog.cancel();
                }
            });

            return builder.create();
        }

        public void onPause() {
            hideSpinnerDropDown(mSpinner);
            super.onPause();
        }

        private void saveSelection(int selectPos) {
            switch (mFileCategory) {
                case CATEGORY_URL:
                    PreferenceManager.setUploadUrlFileExtensionUrlSelectedPos(getActivity(), selectPos);
                    break;
                case CATEGORY_MAPS_URL:
                    PreferenceManager.setUploadMapFileExtensionUrlSelectedPos(getActivity(), selectPos);
                    break;
                default:
                    Log_OC.d(TAG, "Simple text snippet only: no selection to be persisted");
                    break;
            }
        }

        private void hideSpinnerDropDown(Spinner spinner) {
            try {
                Method method = Spinner.class.getDeclaredMethod("onDetachedFromWindow");
                method.setAccessible(true);
                method.invoke(spinner);
            } catch (Exception e) {
                Log_OC.e(TAG, "onDetachedFromWindow", e);
            }
        }

        private void setFilename(EditText inputText, int selectPos) {
            String filename = mFilenameBase.get(selectPos) + mFilenameSuffix.get(selectPos);
            inputText.setText(filename);
            int selectionStart = 0;
            int extensionStart = filename.lastIndexOf(".");
            int selectionEnd = (extensionStart >= 0) ? extensionStart : filename.length();
            if (selectionEnd >= 0) {
                inputText.setSelection(
                        Math.min(selectionStart, selectionEnd),
                        Math.max(selectionStart, selectionEnd));
            }
        }

        private boolean isIntentFromGoogleMap(String subjectText, String extraText) {
            String texts[] = extraText.split("\n");
            if (texts.length != 3)
                return false;
            if (texts[0].length() == 0 || !subjectText.equals(texts[0]))
                return false;
            return texts[2].startsWith("https://goo.gl/maps/");
        }

        private boolean isIntentStartWithUrl(String extraText) {
            return (extraText.startsWith("http://") || extraText.startsWith("https://"));
        }

        @Nullable
        private String renameSafeFilename(String filename) {
            String safeFilename = filename;
            safeFilename = safeFilename.replaceAll("[?]", "_");
            safeFilename = safeFilename.replaceAll("\"", "_");
            safeFilename = safeFilename.replaceAll("/", "_");
            safeFilename = safeFilename.replaceAll("<", "_");
            safeFilename = safeFilename.replaceAll(">", "_");
            safeFilename = safeFilename.replaceAll("[*]", "_");
            safeFilename = safeFilename.replaceAll("[|]", "_");
            safeFilename = safeFilename.replaceAll(";", "_");
            safeFilename = safeFilename.replaceAll("=", "_");
            safeFilename = safeFilename.replaceAll(",", "_");

            try {
                int maxLength = 128;
                if (safeFilename.getBytes(FILENAME_ENCODING).length > maxLength) {
                    safeFilename = new String(safeFilename.getBytes(FILENAME_ENCODING), 0, maxLength, FILENAME_ENCODING);
                }
            } catch (UnsupportedEncodingException e) {
                Log_OC.e(TAG, "rename failed ", e);
                return null;
            }
            return safeFilename;
        }

        private String internetShortcutUrlText(String url) {
            return "[InternetShortcut]\r\n" +
                    "URL=" + url + "\r\n";
        }

        private String internetShortcutWeblocText(String url) {
            return "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
                    "<!DOCTYPE plist PUBLIC \"-//Apple Computer//DTD PLIST 1.0//EN\" \"http://www.apple.com/DTDs/PropertyList-1.0.dtd\">\n" +
                    "<plist version=\"1.0\">\n" +
                    "<dict>\n" +
                    "<key>URL</key>\n" +
                    "<string>" + url + "</string>\n" +
                    "</dict>\n" +
                    "</plist>\n";
        }

        private String internetShortcutDesktopText(String url, String filename) {
            return "[Desktop Entry]\n" +
                    "Encoding=UTF-8\n" +
                    "Name=" + filename + "\n" +
                    "Type=Link\n" +
                    "URL=" + url + "\n" +
                    "Icon=text-html";
        }

        @Nullable
        private File createTempFile(String filename, String text) {
            File file = new File(getActivity().getCacheDir(), filename);
            FileWriter fw = null;
            try {
                fw = new FileWriter(file);
                fw.write(text);
            } catch (IOException e) {
                Log_OC.d(TAG, "Error ", e);
                return null;
            } finally {
                if (fw != null) {
                    try {
                        fw.close();
                    } catch (IOException e) {
                        Log_OC.d(TAG, "Error closing file writer ", e);
                    }
                }
            }
            return file;
        }
    }

    private class SyncBroadcastReceiver extends BroadcastReceiver {

        /**
         * {@link BroadcastReceiver} to enable syncing feedback in UI
         */
        @Override
        public void onReceive(Context context, Intent intent) {
            try {
                String event = intent.getAction();
                Log_OC.d(TAG, "Received broadcast " + event);
                String accountName = intent.getStringExtra(FileSyncAdapter.EXTRA_ACCOUNT_NAME);
                String synchFolderRemotePath =
                        intent.getStringExtra(FileSyncAdapter.EXTRA_FOLDER_PATH);
                RemoteOperationResult synchResult = (RemoteOperationResult)
                        DataHolderUtil.getInstance().retrieve(intent.getStringExtra(FileSyncAdapter.EXTRA_RESULT));
                boolean sameAccount = (getAccount() != null &&
                        accountName.equals(getAccount().name) && getStorageManager() != null);

                if (sameAccount) {

                    if (FileSyncAdapter.EVENT_FULL_SYNC_START.equals(event)) {
                        mSyncInProgress = true;

                    } else {
                        OCFile currentFile = (mFile == null) ? null :
                                getStorageManager().getFileByPath(mFile.getRemotePath());
                        OCFile currentDir = (getCurrentFolder() == null) ? null :
                                getStorageManager().getFileByPath(getCurrentFolder().getRemotePath());

                        if (currentDir == null) {
                            // current folder was removed from the server
                            DisplayUtils.showSnackMessage(
                                    getActivity(),
                                    com.telkomsigma.telkomstorage.R.string.sync_current_folder_was_removed,
                                    getCurrentFolder().getFileName()
                            );
                            browseToRoot();

                        } else {
                            if (currentFile == null && !mFile.isFolder()) {
                                // currently selected file was removed in the server, and now we know it
                                currentFile = currentDir;
                            }

                            if (currentDir.getRemotePath().equals(synchFolderRemotePath)) {
                                populateDirectoryList();
                            }
                            mFile = currentFile;
                        }

                        mSyncInProgress = (!FileSyncAdapter.EVENT_FULL_SYNC_END.equals(event) &&
                                !RefreshFolderOperation.EVENT_SINGLE_FOLDER_SHARES_SYNCED.equals(event));

                        if (RefreshFolderOperation.EVENT_SINGLE_FOLDER_CONTENTS_SYNCED.
                                equals(event) &&
                                /// TODO refactor and make common
                                synchResult != null && !synchResult.isSuccess()) {

                            if (synchResult.getCode() == ResultCode.UNAUTHORIZED ||
                                    (synchResult.isException() && synchResult.getException()
                                            instanceof AuthenticatorException)) {

                                requestCredentialsUpdate(context);

                            } else if (RemoteOperationResult.ResultCode.SSL_RECOVERABLE_PEER_UNVERIFIED.equals(synchResult.getCode())) {

                                showUntrustedCertDialog(synchResult);
                            }
                        }
                    }
                    removeStickyBroadcast(intent);
                    Log_OC.d(TAG, "Setting progress visibility to " + mSyncInProgress);

                }
            } catch (RuntimeException e) {
                // avoid app crashes after changing the serial id of RemoteOperationResult
                // in owncloud library with broadcast notifications pending to process
                removeStickyBroadcast(intent);
                DataHolderUtil.getInstance().delete(intent.getStringExtra(FileSyncAdapter.EXTRA_RESULT));
            }
        }
    }
}
