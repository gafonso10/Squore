/*
 * Copyright (C) 2020  Iddo Hoeve
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

package com.doubleyellow.scoreboard.dialog;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.os.Bundle;
import android.view.KeyEvent;
import android.widget.Button;
/*
import android.os.CountDownTimer;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.widget.LinearLayout;
*/

import com.doubleyellow.scoreboard.R;
import com.doubleyellow.scoreboard.main.ScoreBoard;
import com.doubleyellow.scoreboard.model.Model;
import com.doubleyellow.scoreboard.model.Player;
import com.doubleyellow.scoreboard.vico.IBoard;

/**
 * Dialog in which the referee can either
 * - enter the which end (side of the table in tabletennis) the receiver will start receiving (or server start serving if toss winner decided to receive)
 * - let the app perform a toss
 */
public class SideToss extends BaseAlertDialog
{
    public SideToss(Context context, Model matchModel, ScoreBoard scoreBoard) {
        super(context, matchModel, scoreBoard);
    }

    @Override public boolean storeState(Bundle outState) {
        return true;
    }

    @Override public boolean init(Bundle outState) {
        return true;
    }

    @Override public void show() {
        Player pServer   = matchModel.getServer();
        String sServer   = matchModel.getName(pServer);
        String sReceiver = matchModel.getName(pServer.getOther());
        String sTitle    = context.getString(R.string.sb_what_side_will_x_start_to_y, sReceiver, getString(R.string.sb_receive));
        if ( pServer.equals(ServerToss.m_winnerOfToss) == false ) {
            sTitle   = context.getString(R.string.sb_what_side_will_x_start_to_y, sServer, getString(R.string.sb_serve  )  );
        }
        int iResMessage = matchModel.isDoubles() ? R.string.sb_on_what_side_of_the_scoreboard_should_team_be : R.string.sb_on_what_side_of_the_scoreboard_should_player_be;
        adb.setTitle         (sTitle)
           .setMessage       ("(" + getString(iResMessage) + ")")
           .setIcon          (R.drawable.change_sides)
           .setPositiveButton(R.string.lbl_left           , dialogClickListener)
         //.setNeutralButton (R.string.sb_cmd_toss, null)
           .setNegativeButton(R.string.lbl_right          , dialogClickListener)
           .setOnKeyListener(new DialogInterface.OnKeyListener() {
            @Override public boolean onKey(DialogInterface dialogI, int keyCode, KeyEvent event) {
                int action  = event.getAction();
                if (keyCode == KeyEvent.KEYCODE_BACK /* = 4 */ && action == KeyEvent.ACTION_UP) {
                    AlertDialog dialog = (AlertDialog) dialogI;
                    final Button btnLeft  = dialog.getButton(BTN_LOOSER_OF_TOSS_STARTS_LEFT);
                    final Button btnRight = dialog.getButton(BTN_LOOSER_OF_TOSS_STARTS_RIGHT);
                    if ( btnLeft.isEnabled() == false ) {
                        // toss is performed and RIGHT was selected
                        handleButtonClick(BTN_LOOSER_OF_TOSS_STARTS_RIGHT);
                    } else if ( btnRight.isEnabled() == false ) {
                        // toss is performed and LEFT was selected
                        handleButtonClick(BTN_LOOSER_OF_TOSS_STARTS_LEFT);
                    } else {
                        // no toss performed yet
                        SideToss.this.dismiss();
                    }
                    return true;
                }
                return false;
            }
        });

        adb.setOnDismissListener(new DialogInterface.OnDismissListener() {
            @Override public void onDismiss(DialogInterface dialog) {
                scoreBoard.triggerEvent(ScoreBoard.SBEvent.sideTossDialogEnded, SideToss.this);
            }
        });
/*
        try {
            OnShowListener listener = new OnShowListener(context, ButtonUpdater.iaColorNeutral);
            dialog = adb.show(listener);
        } catch (Exception e) {
            e.printStackTrace();
        }
*/
        dialog = adb.show();
    }

    private DialogInterface.OnClickListener dialogClickListener = new DialogInterface.OnClickListener() {
        @Override public void onClick(DialogInterface dialog, int which) {
            handleButtonClick(which);
        }
    };

    public static final int BTN_LOOSER_OF_TOSS_STARTS_LEFT  = DialogInterface.BUTTON_POSITIVE;
    public static final int BTN_LOOSER_OF_TOSS_STARTS_RIGHT = DialogInterface.BUTTON_NEGATIVE;
//  public static final int BTN_DO_TOSS      = DialogInterface.BUTTON_NEUTRAL;
    @Override public void handleButtonClick(int which) {
        boolean bLooserOfTossIsRight = IBoard.m_firstPlayerOnScreen.equals(ServerToss.m_winnerOfToss);
        Boolean bLooserShouldBeRight = null;
        switch (which){
            case BTN_LOOSER_OF_TOSS_STARTS_LEFT:
                bLooserShouldBeRight = false;
                break;
            case BTN_LOOSER_OF_TOSS_STARTS_RIGHT:
                bLooserShouldBeRight = true;
                break;
        }
        if ( (bLooserShouldBeRight != null) && (bLooserShouldBeRight != bLooserOfTossIsRight)) {
            // swap teams
            scoreBoard.swapSides(null, null);
        }
        if ( bLooserShouldBeRight != null ) {
            this.dismiss();
        }
    }

/*
    private class OnShowListener extends ButtonUpdater {
        private OnShowListener(Context context, int... iButton2Color) {
            super(context, iButton2Color);
        }
        @Override public void onShow(DialogInterface dialogInterface) {
            super.onShow(dialogInterface);

            final Button btnToss = ((AlertDialog)dialogInterface).getButton(BTN_DO_TOSS);
            if (btnToss == null) {
                return;
            }
            ViewParent parent = btnToss.getParent();
            if ( parent instanceof LinearLayout ) {
                ViewGroup.LayoutParams layoutParams = btnToss.getLayoutParams();
                if ( layoutParams instanceof LinearLayout.LayoutParams ) {
                    LinearLayout.LayoutParams llp = (LinearLayout.LayoutParams) layoutParams;
                    llp.weight = 0.8f;
                    btnToss.setLayoutParams(llp);
                }
            }

            // ensure that when toss button is clicked player buttons are toggled a couple of times then only one remains enabled
            btnToss.setOnClickListener(onClickTossListener);

            btnToss.setVisibility(View.GONE); // do not allow toss here for now
        }
    }

    private View.OnClickListener onClickTossListener = new View.OnClickListener() {
        @Override public void onClick(View view) {
            simulateToss();
        }
    };

    private void simulateToss() {
        final Button btnToss  = dialog.getButton(BTN_DO_TOSS );
        final Button btnLeft  = dialog.getButton(BTN_LOOSER_OF_TOSS_STARTS_LEFT);
        final Button btnRight = dialog.getButton(BTN_LOOSER_OF_TOSS_STARTS_RIGHT);
        btnToss.setEnabled(false);
        if ( (btnLeft == null) || (btnRight == null) ) return;

        boolean bInitialEnabledLEFT = Math.round(Math.random()) == 0;
        btnLeft .setEnabled(  bInitialEnabledLEFT );
        btnRight.setEnabled( !bInitialEnabledLEFT );
        CountDownTimer countDownTimer = new CountDownTimer(2400, (80 + Math.abs(System.currentTimeMillis() % 40))) {
            @Override public void onTick(long l) {
                btnLeft .setEnabled( btnLeft .isEnabled() == false );
                btnRight.setEnabled( btnRight.isEnabled() == false );
            }

            @Override public void onFinish() {
                btnToss.setEnabled(true);
                if ( matchModel != null ) {
                    // automatically swap players already, but without closing the dialog
                    boolean bReceiverIsRight = IBoard.m_firstPlayerOnScreen.equals(matchModel.getServer());

                    if ( btnRight.isEnabled() && (bReceiverIsRight == false)) {
                        scoreBoard.swapPlayers(null, null);
                    } else if (btnLeft.isEnabled() && bReceiverIsRight ) {
                        scoreBoard.swapPlayers(null, null);
                    }
                }
            }
        };
        countDownTimer.start();
    }
*/
}
