package com.moac.android.opensecretsanta.fragment;

import android.accounts.Account;
import android.accounts.AccountManager;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TextView;

import com.moac.android.inject.dagger.InjectingDialogFragment;
import com.moac.android.opensecretsanta.R;
import com.moac.android.opensecretsanta.activity.Intents;
import com.moac.android.opensecretsanta.adapter.AccountAdapter;
import com.moac.android.opensecretsanta.database.DatabaseManager;
import com.moac.android.opensecretsanta.model.Group;
import com.moac.android.opensecretsanta.notify.NotifyAuthorization;
import com.moac.android.opensecretsanta.notify.mail.EmailAuthorization;
import com.moac.android.opensecretsanta.notify.sms.SmsPermissionsManager;
import com.moac.android.opensecretsanta.util.AccountUtils;
import com.moac.android.opensecretsanta.util.NotifyUtils;

import java.util.Arrays;

import javax.inject.Inject;

import rx.Observable;
import rx.android.concurrency.AndroidSchedulers;
import rx.concurrency.Schedulers;
import rx.util.functions.Action1;

public class NotifyDialogFragment extends InjectingDialogFragment {

    private static final String TAG = NotifyDialogFragment.class.getSimpleName();
    private static final String MESSAGE_KEY = "message";
    public static final int SMS_PERMISSION_REQUEST_CODE = 53535;

    @Inject
    DatabaseManager mDb;

    @Inject
    SharedPreferences mSharedPreferences;

    @Inject
    AccountManager mAccountManager;

    @Inject
    SmsPermissionsManager mSmsPermissionsManager;

    private EditText mMsgField;
    private Group mGroup;
    private long[] mMemberIds;
    private FragmentContainer mFragmentContainer;
    private Spinner mSpinner;
    private TextView mInfoTextView;
    private ViewGroup mEmailFromContainer;
    private boolean mIsEmailAuthRequired;
    private int mMaxMsgLength;

    // Apparently this is how you retain EditText fields in Dialogs - http://code.google.com/p/android/issues/detail?id=18719
    private String mSavedMsg;
    private TextView mCharCountView;

    public static NotifyDialogFragment create(long groupId, long[] memberIds, String title) {
        Log.i(TAG, "NotifyDialogFragment() - factory creating for groupId: " + groupId + " memberIds: " + Arrays.toString(memberIds));
        NotifyDialogFragment fragment = new NotifyDialogFragment();
        Bundle args = new Bundle();
        args.putLong(Intents.GROUP_ID_INTENT_EXTRA, groupId);
        args.putLongArray(Intents.MEMBER_ID_ARRAY_INTENT_EXTRA, memberIds);
        args.putString(Intents.TITLE_INTENT_EXTRA, title);
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        Log.i(TAG, "onCreateDialog() - start: " + this);
        mMemberIds = getArguments().getLongArray(Intents.MEMBER_ID_ARRAY_INTENT_EXTRA);
        String title = getArguments().getString(Intents.TITLE_INTENT_EXTRA);
        mMaxMsgLength = getResources().getInteger(R.integer.max_notify_msg_length);

        // Inflate layout
        LayoutInflater inflater = getActivity().getLayoutInflater();
        @SuppressLint("InflateParams") // Null parent OK for dialog
        View view = inflater.inflate(R.layout.fragment_dialog_notify, null);

        // Configure the views
        mMsgField = (EditText) view.findViewById(R.id.tv_notify_msg);
        mCharCountView = (TextView) view.findViewById(R.id.textView_characters_remaining);

        // Add the callback to the field
        mMsgField.addTextChangedListener(new TextWatcher() {
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                // Update the reported character length
                setCharactersRemaining(mMaxMsgLength - s.length());
            }
        });

        // Visibility GONE by default
        mEmailFromContainer = (ViewGroup) view.findViewById(R.id.layout_notify_email_container);
        mSpinner = (Spinner) view.findViewById(R.id.spnr_email_selection);
        mInfoTextView = (TextView) view.findViewById(R.id.tv_notify_info);

        final AlertDialog alertDialog = new AlertDialog.Builder(getActivity())
                .setView(view)
                .setTitle(title)
                .setCancelable(true)
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(R.string.notify_send_button_text, null)
                .create();

        // We can't allow the dialog to be dismissed before the onActivityResult expected back
        // from the SmsPermissionsManager is delivered (otherwise onNotifyRequest() is never called
        // when SMS is being sent as the Fragment is usually destroyed before the result is returned.
        // So to work-around this, add this listener which attaches another listener that handles the
        // positive (send) button press and explicitly call dismiss() to once onNotifyRequest() has
        // completed.
        //
        // The OnShowListener is required as the sendButton is null until the View is created.
        // While this listener could just be attached in onStart(), it's easier to read here.
        alertDialog.setOnShowListener(new DialogInterface.OnShowListener() {
            @Override
            public void onShow(DialogInterface dialog) {
                Button sendButton = alertDialog.getButton(AlertDialog.BUTTON_POSITIVE);
                sendButton.setOnClickListener(new OnSendClickedListener());
            }
        });
        return alertDialog;
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);

        // Populate those field that require injected dependencies
        long groupId = getArguments().getLong(Intents.GROUP_ID_INTENT_EXTRA);
        mGroup = mDb.queryById(groupId, Group.class);
        String message = mSavedMsg == null ? mGroup.getMessage() :
                savedInstanceState.getString(MESSAGE_KEY);

        mMsgField.append(message);
        int remainingChars = mMaxMsgLength;
        if (message != null) {
            remainingChars = message.length() >= mMaxMsgLength ? 0 : mMaxMsgLength - message.length();
        }
        setCharactersRemaining(remainingChars);

        mIsEmailAuthRequired = NotifyUtils.containsEmailSendableEntry(mDb, mMemberIds);

        if (mIsEmailAuthRequired) {
            // Add all Gmail accounts to list
            final Observable<Account[]> accountsObservable = AccountUtils.getAllGmailAccountsObservable(getActivity(), mAccountManager);
            accountsObservable.
                    subscribeOn(Schedulers.newThread()).
                    observeOn(AndroidSchedulers.mainThread()).
                    subscribe(new Action1<Account[]>() {
                                  @Override
                                  public void call(Account[] accounts) {
                                      AccountAdapter aa = new AccountAdapter(getActivity(), accounts);
                                      mSpinner.setAdapter(aa);
                                      mEmailFromContainer.setVisibility(View.VISIBLE);
                                      // TODO Set email to preference
                                  }
                              },
                            new Action1<Throwable>() {
                                @Override
                                public void call(Throwable throwable) {
                                    mInfoTextView.setText(throwable.getMessage());
                                    mInfoTextView.setVisibility(View.VISIBLE);
                                }
                            }
                    );
        }
    }

    protected void setCharactersRemaining(int charsRemaining) {
        mCharCountView.setText(getString(R.string.characters_remaining_message_unformatted, charsRemaining));
        if (mMsgField.length() >= mMaxMsgLength) {
            mCharCountView.setTextColor(getResources().getColor(R.color.accent_color));
        } else {
            mCharCountView.setTextColor(getResources().getColor(R.color.text_neutral_color));
        }
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putString(MESSAGE_KEY, mMsgField.getText().toString());
        Log.d(TAG, "onSaveInstanceState() msg: " + outState.getString(MESSAGE_KEY));
        mSavedMsg = outState.getString(MESSAGE_KEY);
    }

    @Override
    public void onDestroyView() {
        // Refer to - http://code.google.com/p/android/issues/detail?id=17423
        if (getDialog() != null && getRetainInstance())
            getDialog().setDismissMessage(null);
        super.onDestroyView();
    }

    @Override
    public void onAttach(Activity activity) {
        super.onAttach(activity);
        try {
            mFragmentContainer = (FragmentContainer) activity;
        } catch (ClassCastException e) {
            throw new ClassCastException(activity.toString() + " must implement FragmentContainer");
        }
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        Log.i(TAG, "onActivityResult() - requestCode: " + requestCode + " resultCode: " + resultCode);
        if (requestCode == SMS_PERMISSION_REQUEST_CODE && resultCode == Activity.RESULT_OK) {
            // Send the result
            onNotifyRequested();
        }
    }

    protected void onNotifyRequested() {
        final NotifyAuthorization.Builder auth = new NotifyAuthorization.Builder();

        if (mIsEmailAuthRequired) {
            Account acc = (Account) mSpinner.getSelectedItem();
            if (acc != null) {
                // Set the selected email as the user preference
                String emailPrefKey = getActivity().getString(R.string.gmail_account_preference);
                mSharedPreferences.edit().putString(emailPrefKey, acc.name).apply();

                AccountUtils.getPreferedGmailAuth(getActivity(), mAccountManager, mSharedPreferences, getActivity()).
                        subscribeOn(Schedulers.newThread()).
                        observeOn(AndroidSchedulers.mainThread()).subscribe(new Action1<EmailAuthorization>() {
                    @Override
                    public void call(EmailAuthorization emailAuth) {
                        Log.d(TAG, "call() - got EmailAuthorization: " + emailAuth.getEmailAddress() + ":" + emailAuth.getToken());
                        auth.withAuth(emailAuth);
                        mGroup.setMessage(mMsgField.getText().toString().trim());
                        mDb.update(mGroup);
                        executeNotifyDraw(auth.build(), mGroup, mMemberIds);
                    }
                });
            }  // else no email auth available - do nothing.
        } else {
            // We have no additional authorization - just send as is
            // Get the custom message.
            mGroup.setMessage(mMsgField.getText().toString().trim());
            mDb.update(mGroup);
            executeNotifyDraw(auth.build(), mGroup, mMemberIds);
        }
        this.dismiss();
    }

    private void executeNotifyDraw(NotifyAuthorization auth, Group group, long[] members) {
        mFragmentContainer.executeNotifyDraw(auth, group, members);
    }

    private class OnSendClickedListener implements View.OnClickListener {

        @Override
        public void onClick(View v) {
            if (NotifyUtils.requiresSmsPermission(getActivity(), mDb, mMemberIds)) {
                mSmsPermissionsManager
                        .requestDefaultSmsPermission(getActivity().getApplicationContext(),
                                NotifyDialogFragment.this,
                                SMS_PERMISSION_REQUEST_CODE);
            } else {
                onNotifyRequested();
            }
        }
    }

    public interface FragmentContainer {
        public void executeNotifyDraw(NotifyAuthorization auth, Group group, long[] members);
    }
}
