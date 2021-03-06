package com.moac.android.opensecretsanta.notify;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import com.moac.android.opensecretsanta.database.DatabaseManager;
import com.moac.android.opensecretsanta.model.Assignment;
import com.moac.android.opensecretsanta.model.Group;
import com.moac.android.opensecretsanta.model.Member;
import com.moac.android.opensecretsanta.notify.mail.EmailNotifier;
import com.moac.android.opensecretsanta.notify.mail.EmailTransporter;
import com.moac.android.opensecretsanta.notify.sms.SmsNotifier;
import com.moac.android.opensecretsanta.notify.sms.SmsTransporter;
import com.squareup.otto.Bus;
import rx.Observable;
import rx.Observer;
import rx.Subscription;
import rx.subscriptions.Subscriptions;

public class DefaultNotifyExecutor implements NotifyExecutor {

    private final Context mContext;
    private final NotifyAuthorization mAuth;
    private final DatabaseManager mDb;
    private final Bus mBus;

    private static final String TAG = DefaultNotifyExecutor.class.getSimpleName();
    private final SmsTransporter mSmsTransporter;
    private final EmailTransporter mEmailTransporter;

    public DefaultNotifyExecutor(Context context, NotifyAuthorization auth, DatabaseManager db, Bus bus,
                                 SmsTransporter smsTransporter, EmailTransporter emailTransporter) {
        mContext = context;
        mAuth = auth;
        mDb = db;
        mBus = bus;
        mSmsTransporter = smsTransporter;
        mEmailTransporter = emailTransporter;
    }

    @Override
    public Observable<NotifyStatusEvent> notifyDraw(final Group group, final long[] memberIds) {
        return Observable.create(new Observable.OnSubscribeFunc<NotifyStatusEvent>() {
            @Override
            public Subscription onSubscribe(Observer<? super NotifyStatusEvent> observer) {

                // TODO Think better about how to handle failures - should we reset all first?
                Handler handler = new Handler(Looper.getMainLooper());

                // Iterate through the provided members - get their Assignment.
                for(long memberId : memberIds) {
                    Member member = mDb.queryById(memberId, Member.class);
                    Assignment assignment = mDb.queryAssignmentForMember(member.getId());

                    // Fatal error - must be have an assignment
                    if(assignment == null) {
                        Log.e(TAG, "executeNotify() - No Assignment for Member: " + member.getName());
                        observer.onError(new Exception("No Assignment for Member: " + member.getName()));
                        return Subscriptions.empty();
                    }

                    Log.i(TAG, "executeNotify() - preparing Assignment: " + assignment);

                    Member giftReceiver = mDb.queryById(assignment.getReceiverMemberId(), Member.class);

                    switch(member.getContactMethod()) {
                        case SMS:
                            Log.i(TAG, "executeNotify() - Building SMS Notifier for: " + member.getName());
                            // Reset the existing state
                            assignment.setSendStatus(Assignment.Status.Assigned);
                            observer.onNext(new NotifyStatusEvent(assignment));
                            mDb.update(assignment);

                            // Build the notifier and execute
                            SmsNotifier smsNotifier = new SmsNotifier(mContext, mSmsTransporter);
                            smsNotifier.notify(assignment, member, giftReceiver.getName(), group.getMessage());
                            break;
                        case EMAIL:
                            Log.i(TAG, "executeNotify() - Building Email Notifier for: " + member.getName());
                            // Reset the existing state
                            assignment.setSendStatus(Assignment.Status.Assigned);
                            observer.onNext(new NotifyStatusEvent(assignment));
                            mDb.update(assignment);

                            // Get the authorization
                            if(mAuth.getEmailAuth() == null) {
                                // This is fatal
                                assignment.setSendStatus(Assignment.Status.Failed);
                                mDb.update(assignment);
                                observer.onNext(new NotifyStatusEvent(assignment));
                                observer.onError(new Exception("Error - Not authorized to use your email account"));
                                return Subscriptions.empty();
                            }

                            // Build the notifier with auth and execute
                            String senderEmail = mAuth.getEmailAuth().getEmailAddress();
                            String token = mAuth.getEmailAuth().getToken();
                            EmailNotifier emailNotifier = new EmailNotifier(mContext, mBus, mDb,
                              handler, mEmailTransporter, senderEmail, token);
                            emailNotifier.notify(assignment, member, giftReceiver.getName(), group.getMessage());
                            break;
                        case REVEAL_ONLY:
                            break;
                        default:
                            Log.e(TAG, "executeNotify() - Unsupported contact mode: " + member.getContactMethod());
                    }
                }
                observer.onCompleted();
                return Subscriptions.empty();
            }
        });
    }
}