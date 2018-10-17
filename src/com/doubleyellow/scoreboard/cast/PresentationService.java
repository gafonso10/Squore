/*
 * Copyright (C) 2017  Iddo Hoeve
 *
 * Squore is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.doubleyellow.scoreboard.cast;

import android.annotation.TargetApi;
import android.os.Build;
import android.util.Log;
import android.view.Display;
import com.doubleyellow.scoreboard.main.ScoreBoard;
import com.doubleyellow.scoreboard.model.*;
import com.doubleyellow.scoreboard.prefs.ColorPrefs;
import com.doubleyellow.scoreboard.timer.TimerView;
import com.google.android.gms.cast.CastRemoteDisplayLocalService;

import java.util.Map;

@TargetApi(Build.VERSION_CODES.KITKAT)
public class PresentationService extends CastRemoteDisplayLocalService
{
    private static final String TAG = "SB.PresentationService";

    @Override public void onCreatePresentation(Display display) {
        createPresentation(display);
    }

    @Override public void onDismissPresentation() {
        dismissPresentation();
    }

    private static PresentationScreen mPresentation = null;

    public static boolean isCasting() {
        return (mPresentation != null);
    }
    private void dismissPresentation() {
        if (mPresentation != null) {
            mPresentation.dismiss();
            mPresentation = null;
            int iRemoved = ScoreBoard.matchModel.clearListeners(".*PresentationService.*");
            Log.d(TAG, "Removed listeners " + iRemoved);
        }
    }

    private void createPresentation(Display display) {
        dismissPresentation();
        mPresentation = new PresentationScreen(this, display);
        try {
            mPresentation.show();
        } catch (Exception ex) {
            Log.e(TAG, "Unable to show presentation, display was removed.", ex);
            dismissPresentation();
        }
    }
    static boolean setModel(Model model) {
        if ( mPresentation != null ) {
            mPresentation.setModel(model);
            return true;
        }
        return false;
    }
    static TimerView getTimerView() {
        if ( mPresentation != null ) {
            return mPresentation.getTimerView();
        }
        return null;
    }
    static boolean refreshColors(Map<ColorPrefs.ColorTarget, Integer> mColors) {
        if ( mPresentation != null ) {
            mPresentation.refreshColors(mColors);
            return true;
        }
        return false;
    }
    static boolean refreshDurationChronos() {
        if ( mPresentation != null ) {
            mPresentation.refreshDurationChronos();
            return true;
        }
        return false;
    }
    static boolean refreshGamesWonAppearance() {
        if ( mPresentation != null ) {
            mPresentation.refreshGamesWonAppearance();
            return true;
        }
        return false;
    }
}
