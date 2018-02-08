/**
 * ownCloud Android client application
 *
 * @author David A. Velasco
 * Copyright (C) 2015 ownCloud Inc.
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

package com.telkomsigma.telkomstorage.ui.dialog;

import android.app.Dialog;
import android.content.DialogInterface;
import android.graphics.PorterDuff;
import android.os.Bundle;
import android.support.v4.app.DialogFragment;
import android.support.v7.app.AlertDialog;
import android.view.LayoutInflater;
import android.view.View;
import android.view.WindowManager.LayoutParams;
import android.widget.EditText;
import android.widget.TextView;

import com.owncloud.android.lib.resources.files.FileUtils;
import com.telkomsigma.telkomstorage.R;
import com.telkomsigma.telkomstorage.datamodel.OCFile;
import com.telkomsigma.telkomstorage.ui.activity.ComponentsGetter;
import com.telkomsigma.telkomstorage.utils.DisplayUtils;
import com.telkomsigma.telkomstorage.utils.ThemeUtils;

/**
 *  Dialog to input the name for a new folder to create.  
 *
 *  Triggers the folder creation when name is confirmed.
 */
public class CreateFolderDialogFragment
        extends DialogFragment implements DialogInterface.OnClickListener {

    public static final String CREATE_FOLDER_FRAGMENT = "CREATE_FOLDER_FRAGMENT";
    private static final String ARG_PARENT_FOLDER = "PARENT_FOLDER";
    private OCFile mParentFolder;

    /**
     * Public factory method to create new CreateFolderDialogFragment instances.
     *
     * @param parentFolder            Folder to create
     * @return Dialog ready to show.
     */
    public static CreateFolderDialogFragment newInstance(OCFile parentFolder) {
        CreateFolderDialogFragment frag = new CreateFolderDialogFragment();
        Bundle args = new Bundle();
        args.putParcelable(ARG_PARENT_FOLDER, parentFolder);
        frag.setArguments(args);
        return frag;

    }

    @Override
    public void onStart() {
        super.onStart();

        int color = ThemeUtils.primaryAccentColor();

        AlertDialog alertDialog = (AlertDialog) getDialog();

        alertDialog.getButton(AlertDialog.BUTTON_POSITIVE).setTextColor(color);
        alertDialog.getButton(AlertDialog.BUTTON_NEGATIVE).setTextColor(color);
    }

    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        int accentColor = ThemeUtils.primaryAccentColor();
        mParentFolder = getArguments().getParcelable(ARG_PARENT_FOLDER);

        // Inflate the layout for the dialog
        LayoutInflater inflater = getActivity().getLayoutInflater();
        View v = inflater.inflate(R.layout.edit_box_dialog, null);

        // Setup layout 
        EditText inputText = v.findViewById(R.id.user_input);
        inputText.setText("");
        inputText.requestFocus();
        inputText.getBackground().setColorFilter(accentColor, PorterDuff.Mode.SRC_ATOP);

        // Build the dialog  
        AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
        builder.setView(v)
                .setPositiveButton(R.string.common_ok, this)
                .setNegativeButton(R.string.common_cancel, this)
                .setTitle(ThemeUtils.getColoredTitle(getResources().getString(R.string.uploader_info_dirname),
                        accentColor));
        Dialog d = builder.create();
        d.getWindow().setSoftInputMode(LayoutParams.SOFT_INPUT_STATE_VISIBLE);
        return d;
    }


    @Override
    public void onClick(DialogInterface dialog, int which) {
        if (which == AlertDialog.BUTTON_POSITIVE) {
            String newFolderName =
                    ((TextView) (getDialog().findViewById(R.id.user_input)))
                            .getText().toString().trim();

            if (newFolderName.length() <= 0) {
                DisplayUtils.showSnackMessage(getActivity(), R.string.filename_empty);
                return;
            }
            boolean serverWithForbiddenChars = ((ComponentsGetter) getActivity()).
                    getFileOperationsHelper().isVersionWithForbiddenCharacters();

            if (!FileUtils.isValidName(newFolderName, serverWithForbiddenChars)) {

                if (serverWithForbiddenChars) {
                    DisplayUtils.showSnackMessage(getActivity(), R.string.filename_forbidden_charaters_from_server);
                } else {
                    DisplayUtils.showSnackMessage(getActivity(), R.string.filename_forbidden_characters);
                }

                return;
            }

            String path = mParentFolder.getRemotePath();
            path += newFolderName + OCFile.PATH_SEPARATOR;
            ((ComponentsGetter) getActivity()).
                    getFileOperationsHelper().createFolder(path, false);
        }
    }
}