package com.moac.android.opensecretsanta.adapter;

import com.moac.android.opensecretsanta.activity.ContactModes;

public class ContactModeRowDetails {

    protected int mContactMode;
    protected String mContactDetail;

    public ContactModeRowDetails(int contactMode, String contactDetail) {
        mContactMode = contactMode;
        mContactDetail = contactDetail;
    }

    public int getContactMode() { return mContactMode; }
    public String getContactDetail() { return mContactDetail; }

    @Override
    public boolean equals(Object other) {
        if(this == other) return true;
        if(!(other instanceof ContactModeRowDetails)) return false;

        ContactModeRowDetails that = (ContactModeRowDetails) other;

        return this.mContactMode == that.mContactMode
          && (null == this.mContactDetail ? (this.mContactDetail == that.mContactDetail) : this.mContactDetail.equals(that.mContactDetail));
    }

    @Override
    public int hashCode() {
        int hash = 7;
        hash = 31 * hash + mContactMode;
        hash = 31 * hash + (null == mContactDetail ? 0 : mContactDetail.hashCode());
        return hash;
    }

    @Override
    public String toString() {
        switch(mContactMode) {
            case ContactModes.NAME_ONLY_CONTACT_MODE:
                return "View draw result on this phone";
            case ContactModes.SMS_CONTACT_MODE:
                return "(SMS) " + mContactDetail;
            case ContactModes.EMAIL_CONTACT_MODE:
                return "(Email) " + mContactDetail;
            default:
                return "Unsupported Mode";
        }
    }
}
