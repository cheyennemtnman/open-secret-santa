package com.moac.android.opensecretsanta.model;

import com.j256.ormlite.field.DatabaseField;
import com.j256.ormlite.table.DatabaseTable;

@DatabaseTable(tableName = DrawResultEntry.TABLE_NAME)
public class DrawResultEntry extends PersistableObject {

    public enum Status {
        Not_Sent("Not Sent"), Successful("Successful"), Failed("Failed");

        private String mText;
        Status(String _text) {  mText = _text; }
        public String getText() { return mText; }
    }

    public static final String TABLE_NAME =  "draw_result_entries";

    public static final long UNSET_DATE = -1;

    public static interface Columns extends PersistableObject.Columns {
        public static final String DRAW_RESULT_ID_COLUMN = "DRAW_RESULT_ID";
        public static final String GIVER_MEMBER_ID_COLUMN = "GIVER_MEMBER_ID";
        public static final String RECEIVER_MEMBER_ID_COLUMN = "RECEIVER_MEMBER_ID";
        public static final String VIEWED_DATE_COLUMN = "VIEWED_DATE";
        public static final String SENT_DATE_COLUMN = "SENT_DATE";
        public static final String SEND_STATUS_COLUMN = "SEND_STATUS";
    }

    /**
     * uniqueCombo with mGiver and mDrawResult only prevents a giver having multiple recipients,
     * rather than a recipient having multiple givers. I don't think there's a way to support
     * two separate combos in ORMLite - eg mGiver/mDrawResult & mReceiver/mDrawResult.
     *
     * Perhaps this is an argument for is associating the DRE with the Members only and
     * not also the DrawResult (which perhaps could be discarded)
     */

    @DatabaseField(columnName = Columns.GIVER_MEMBER_ID_COLUMN, foreign = true, canBeNull = false, uniqueCombo = true,
      columnDefinition = "integer references members (_id) on delete cascade")
    private Member mGiver;

    @DatabaseField(columnName = Columns.RECEIVER_MEMBER_ID_COLUMN, foreign = true, canBeNull = false,
      columnDefinition = "integer references members (_id) on delete cascade")
    private Member mReceiver;

    @DatabaseField(columnName = Columns.DRAW_RESULT_ID_COLUMN, foreign = true, canBeNull = false, uniqueCombo = true,
      columnDefinition = "integer references draw_results (_id) on delete cascade")
    private DrawResult mDrawResult;

    @DatabaseField(columnName = Columns.VIEWED_DATE_COLUMN)
    private long mViewedDate = UNSET_DATE;

    @DatabaseField(columnName = Columns.SENT_DATE_COLUMN)
    private long mSentDate = UNSET_DATE;

    @DatabaseField(columnName = Columns.SEND_STATUS_COLUMN)
    private Status mSendStatus = Status.Not_Sent;

    public long getDrawResultId() { return mDrawResult.getId(); }
    public void setDrawResult(DrawResult _drawResult) { mDrawResult = _drawResult; }

    public long getGiverMemberId() { return mGiver.getId(); }
    public void setGiverMember(Member _giver) { mGiver = _giver; }

    public long getReceiverMemberId() { return mReceiver.getId(); }
    public void setReceiverMember(Member _receiver) { mReceiver = _receiver; }

    public long getViewedDate() { return mViewedDate; }
    public void setViewedDate(long _viewedDate) { mViewedDate = _viewedDate; }

    public long getSentDate() { return mSentDate; }
    public void setSentDate(long _sentDate) { mSentDate = _sentDate; }

    public Status getSendStatus() { return mSendStatus; }
    public void setSendStatus(Status _sendStatus) { mSendStatus = _sendStatus; }

}
