
package org.smg.server.servlet.container;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.smg.server.database.ContainerDatabaseDriver;
import org.smg.server.servlet.container.GameApi.GameState;
import org.smg.server.util.CORSUtil;
import org.smg.server.util.IDUtil;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.appengine.api.datastore.Entity;
import com.google.appengine.labs.repackaged.org.json.JSONException;
import com.google.appengine.labs.repackaged.org.json.JSONObject;
import com.google.appengine.labs.repackaged.org.json.JSONArray;
import com.google.common.collect.Lists;

public class StateServlet extends HttpServlet {

  @Override
  protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException,
      IOException {
    CORSUtil.addCORSHeader(resp);
    JSONObject returnValue = new JSONObject();

    // verify matchId
    String mId = req.getPathInfo().substring(1);
    long matchId = 0;
    try {
      matchId = IDUtil.stringToLong(mId);
    } catch (Exception e) {
      ContainerVerification.sendErrorMessage(
          resp, returnValue, ContainerConstants.WRONG_MATCH_ID);
      return;
    }
    if (!ContainerVerification.matchIdVerify(matchId)) {
      ContainerVerification.sendErrorMessage(
          resp, returnValue, ContainerConstants.WRONG_MATCH_ID);
      return;
    }
    // verify playerId
    String pId = String.valueOf(req.getParameter(ContainerConstants.PLAYER_ID));
    long playerId = 0;
    try {
      playerId = IDUtil.stringToLong(pId);
    } catch (Exception e) {
      ContainerVerification.sendErrorMessage(
          resp, returnValue, ContainerConstants.WRONG_PLAYER_ID);
      return;
    }
    if (!ContainerVerification.playerIdVerify(playerId)) {
      ContainerVerification.sendErrorMessage(
          resp, returnValue, ContainerConstants.WRONG_PLAYER_ID);
      return;
    }
    // verify accessSignature
    String accessSignature = req.getParameter(ContainerConstants.ACCESS_SIGNATURE);
    if (!ContainerVerification.accessSignatureVerify(accessSignature, playerId)) {
      ContainerVerification.sendErrorMessage(
          resp, returnValue, ContainerConstants.WRONG_ACCESS_SIGNATURE);
      return;
    }

    Entity stateForPlayerEntity = ContainerDatabaseDriver.getEntityByKey(ContainerConstants.MATCH,
        matchId);
    MatchInfo mi;
    try {
      mi = MatchInfo.getMatchInfoFromEntity(stateForPlayerEntity);
    } catch (JSONException e2) {
      try {
        returnValue.put(ContainerConstants.ERROR, ContainerConstants.NO_MATCH_FOUND);
        returnValue.write(resp.getWriter());
      } catch (JSONException e) {
      }
      return;
    }

    GameState state;
    List<Map<String, Object>> lastMove;

    // At this time, the first player hasn't made the initial move.
    if (mi.getHistory().size() == 0) {
      state = new GameState();
      lastMove = Lists.newArrayList();
    } else {
      state = mi.getHistory().get(0).getGameState();
      lastMove = mi.getHistory().get(0).getLastMove();
    }

    try {
      returnValue.put(ContainerConstants.MATCH_ID, String.valueOf(matchId));
      returnValue
          .put(ContainerConstants.STATE, state.getStateForPlayerId(String.valueOf(playerId)));
      String jsnStrTmp = new ObjectMapper().writeValueAsString(GameStateHelper
          .getOperationsListForPlayer(GameStateHelper.messageToOperationList(lastMove),
              state.getVisibleTo(),
              String.valueOf(playerId)));
      returnValue.put(ContainerConstants.LAST_MOVE, new JSONArray(jsnStrTmp));
    } catch (JSONException e1) {
    }
    try {
      returnValue.write(resp.getWriter());
    } catch (JSONException e) {
    }
  }

  @Override
  protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException,
      IOException {
    super.doPost(req, resp);
  }
}