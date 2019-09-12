/*
 * Copyright (c) 2019 Hai Zhang <dreaming.in.code.zh@gmail.com>
 * All Rights Reserved.
 */

package me.zhanghai.android.files.fileproperties.permissions;

import android.app.Dialog;
import android.content.Context;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.CheckBox;
import android.widget.EditText;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatDialogFragment;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import butterknife.BindView;
import butterknife.ButterKnife;
import me.zhanghai.android.files.R;
import me.zhanghai.android.files.compat.AlertDialogBuilderCompat;
import me.zhanghai.android.files.filejob.FileJobService;
import me.zhanghai.android.files.filelist.FileItem;
import me.zhanghai.android.files.provider.common.ByteString;
import me.zhanghai.android.files.provider.common.PosixFileAttributes;
import me.zhanghai.android.files.provider.common.PosixUser;
import me.zhanghai.android.files.util.FragmentUtils;
import me.zhanghai.android.files.util.SelectionLiveData;
import me.zhanghai.android.files.util.ViewUtils;
import me.zhanghai.java.functional.Functional;

public class SetOwnerDialogFragment extends AppCompatDialogFragment {

    private static final String KEY_PREFIX = SetOwnerDialogFragment.class.getName() + '.';

    private static final String EXTRA_FILE = KEY_PREFIX + "FILE";

    private FileItem mExtraFile;

    @BindView(R.id.filter)
    EditText mFilterEdit;
    @BindView(R.id.recycler)
    RecyclerView mRecyclerView;
    @BindView(R.id.recursive)
    CheckBox mRecursiveCheckBox;

    private SetOwnerViewModel mViewModel;

    private UserListAdapter mAdapter;

    private Integer mPendingScrollToUid;

    @NonNull
    public static SetOwnerDialogFragment newInstance(@NonNull FileItem file) {
        //noinspection deprecation
        SetOwnerDialogFragment fragment = new SetOwnerDialogFragment();
        FragmentUtils.getArgumentsBuilder(fragment)
                .putParcelable(EXTRA_FILE, file);
        return fragment;
    }

    public static void show(@NonNull FileItem file, @NonNull Fragment fragment) {
        SetOwnerDialogFragment.newInstance(file)
                .show(fragment.getChildFragmentManager(), null);
    }

    /**
     * @deprecated Use {@link #newInstance(FileItem)} instead.
     */
    public SetOwnerDialogFragment() {}

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mExtraFile = getArguments().getParcelable(EXTRA_FILE);
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {

        AlertDialog.Builder builder = AlertDialogBuilderCompat.create(requireContext(), getTheme())
                .setTitle(R.string.file_properties_permissions_set_owner_title);
        Context context = builder.getContext();
        View contentView = ViewUtils.inflate(R.layout.set_owner_dialog, context);
        ButterKnife.bind(this, contentView);

        mViewModel = new ViewModelProvider(this).get(SetOwnerViewModel.class);
        SelectionLiveData<Integer> selectionLiveData = mViewModel.getSelectionLiveData();
        if (selectionLiveData.getValue() == null) {
            PosixFileAttributes attributes = (PosixFileAttributes) mExtraFile.getAttributes();
            int uid = attributes.owner().getId();
            selectionLiveData.setValue(uid);
            mPendingScrollToUid = uid;
        }

        mFilterEdit.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(@NonNull CharSequence text, int start, int count,
                                          int after) {}
            @Override
            public void onTextChanged(@NonNull CharSequence text, int start, int before,
                                      int count) {}
            @Override
            public void afterTextChanged(@NonNull Editable text) {
                mViewModel.setFilter(text.toString());
            }
        });
        mRecyclerView.setLayoutManager(new LinearLayoutManager(context));
        mAdapter = new UserListAdapter(this, selectionLiveData);
        mRecyclerView.setAdapter(mAdapter);
        selectionLiveData.observe(this, mAdapter);

        mViewModel.getFilteredUserListLiveData().observe(this, this::onFilteredUserListChanged);

        return builder
                .setView(contentView)
                .setPositiveButton(android.R.string.ok, (dialog1, which) -> setOwner())
                .setNegativeButton(android.R.string.cancel, null)
                .create();
    }

    private void onFilteredUserListChanged(@NonNull UserListData userListData) {
        switch (userListData.state) {
            case LOADING:
                break;
            case ERROR:
                break;
            case SUCCESS:
                mAdapter.replace(userListData.data);
                if (mPendingScrollToUid != null) {
                    int position = mAdapter.findPositionByUid(mPendingScrollToUid);
                    if (position != RecyclerView.NO_POSITION) {
                        mRecyclerView.scrollToPosition(position);
                    }
                    mPendingScrollToUid = null;
                }
                break;
            default:
                throw new AssertionError();
        }
    }

    private void setOwner() {
        int uid = mViewModel.getSelectionLiveData().getValue();
        boolean recursive = mRecursiveCheckBox.isChecked();
        if (!recursive) {
            PosixFileAttributes attributes = (PosixFileAttributes) mExtraFile.getAttributes();
            if (uid == attributes.owner().getId()) {
                return;
            }
        }
        UserListData userListData = mViewModel.getUserListData();
        if (userListData.data == null) {
            return;
        }
        UserItem user = Functional.find(userListData.data, user_ -> user_.uid == uid);
        if (user == null) {
            return;
        }
        PosixUser owner = new PosixUser(uid, ByteString.fromStringOrNull(user.name));
        FileJobService.setOwner(mExtraFile.getPath(), owner, recursive, requireContext());
    }
}
