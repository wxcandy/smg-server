
package org.smg.server.servlet.container;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.smg.server.database.ContainerDatabaseDriver;
import org.smg.server.database.DatabaseDriverPlayer;
import org.smg.server.database.EndGameDatabaseDriver;
import org.smg.server.database.models.Player;
import org.smg.server.database.models.Player.PlayerProperty;
import org.smg.server.servlet.container.GameApi.AttemptChangeTokens;
import org.smg.server.servlet.container.GameApi.EndGame;
import org.smg.server.servlet.container.GameApi.GameState;
import org.smg.server.servlet.container.GameApi.Message;
import org.smg.server.servlet.container.GameApi.Operation;
import org.smg.server.servlet.container.GameApi.SetTurn;
import org.smg.server.util.CORSUtil;
import org.smg.server.util.IDUtil;
import org.smg.server.util.JSONUtil;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.appengine.api.channel.ChannelMessage;
import com.google.appengine.api.channel.ChannelService;
import com.google.appengine.api.channel.ChannelServiceFactory;
import com.google.appengine.api.datastore.Entity;
import com.google.appengine.api.datastore.EntityNotFoundException;
import com.google.appengine.labs.repackaged.org.json.JSONException;
import com.google.appengine.labs.repackaged.org.json.JSONObject;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;

@SuppressWarnings("serial")
public class MatchOperationServlet extends HttpServlet {

  @Override
  public void doOptions(HttpServletRequest req, HttpServletResponse resp) throws IOException {
    CORSUtil.addCORSHeader(resp);
  }

  @Override
  protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException,
      IOException {

    CORSUtil.addCORSHeader(resp);
    JSONObject returnValue = new JSONObject();
    // verify matchId
    if (req.getPathInfo().length() < 2) {
      ContainerVerification.sendErrorMessage(
          resp, returnValue, ContainerConstants.WRONG_MATCH_ID);
      return;
    }
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
    if (!req.getParameterMap().containsKey(ContainerConstants.PLAYER_ID)) {
      ContainerVerification.sendErrorMessage(
          resp, returnValue, ContainerConstants.PLAYER_ID);
      return;
    }
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
    if (!req.getParameterMap().containsKey(ContainerConstants.ACCESS_SIGNATURE)) {
      ContainerVerification.sendErrorMessage(
          resp, returnValue, ContainerConstants.WRONG_ACCESS_SIGNATURE);
      return;
    }
    String accessSignature = req.getParameter(ContainerConstants.ACCESS_SIGNATURE);
    if (!ContainerVerification.accessSignatureVerify(accessSignature, playerId)) {
      ContainerVerification.sendErrorMessage(
          resp, returnValue, ContainerConstants.WRONG_ACCESS_SIGNATURE);
      return;
    }

    // Return the MatchInfo entity info. I don't think this is a good way. The
    // info should be generated by MatchInfo class.
    // TODO
    Entity entity = ContainerDatabaseDriver.getEntityByKey(ContainerConstants.MATCH, matchId);
    Map<String, Object> propsMap = entity.getProperties();
    for (String key : propsMap.keySet()) {
      Object val = propsMap.get(key);
      try {
        returnValue.put(key, val);
      } catch (JSONException e) {
        e.printStackTrace();
      }
    }

    try {
      returnValue.write(resp.getWriter());
    } catch (JSONException e) {
    }
  }

  @SuppressWarnings("unchecked")
  @Override
  protected void doPost(HttpServletRequest req, HttpServletResponse resp)
      throws ServletException, IOException {
    CORSUtil.addCORSHeader(resp);
    // get json string the parse to map
    String json = Utils.getBody(req);
    JSONObject returnValue = new JSONObject();
    if (json != null && !json.isEmpty()) {
      Map<String, Object> jsonMap = null;
      try {
        jsonMap = JSONUtil.parse(json);
      } catch (IOException e) {
        ContainerVerification.sendErrorMessage(
            resp, returnValue, ContainerConstants.JSON_PARSE_ERROR);
        return;
      }

      // check if missing info
      if (!jsonMap.containsKey(ContainerConstants.PLAYER_IDS)
          || !jsonMap.containsKey(ContainerConstants.ACCESS_SIGNATURE)
          || !jsonMap.containsKey(ContainerConstants.OPERATIONS)) {
        ContainerVerification.sendErrorMessage(
            resp, returnValue, ContainerConstants.MISSING_INFO);
        return;
      }
      // verify playerIds
      ArrayList<String> ids =
          (ArrayList<String>) jsonMap.get(ContainerConstants.PLAYER_IDS);
      List<Long> playerIds = new ArrayList<Long>();
      try {
        playerIds = IDUtil.stringListToLongList(ids);
      } catch (Exception e) {
        ContainerVerification.sendErrorMessage(
            resp, returnValue, ContainerConstants.WRONG_PLAYER_ID);
        return;
      }
      if (!ContainerVerification.playerIdsVerify(playerIds)) {
        ContainerVerification.sendErrorMessage(
            resp, returnValue, ContainerConstants.WRONG_PLAYER_ID);
        return;
      }
      // verify accessSignature
      String accessSignature = String.valueOf(jsonMap.get(ContainerConstants.ACCESS_SIGNATURE));
      if (!ContainerVerification.accessSignatureVerify(accessSignature, playerIds)) {
        ContainerVerification.sendErrorMessage(
            resp, returnValue, ContainerConstants.WRONG_ACCESS_SIGNATURE);
        return;
      }
      // verify matchId
      if (req.getPathInfo().length() < 2) {
        ContainerVerification.sendErrorMessage(
            resp, returnValue, ContainerConstants.WRONG_MATCH_ID);
        return;
      }
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

      // Get entity for MatchInfo from database.
      Entity entity = ContainerDatabaseDriver.getEntityByKey(ContainerConstants.MATCH, matchId);

      // Get current playerId
      long currentPlayerId = -1;
      for (Long pid : playerIds) {
        Player currentPlayer;
        try {
          currentPlayer = DatabaseDriverPlayer.getPlayerById(pid);
          if (currentPlayer.getProperty(PlayerProperty.accessSignature).equals(accessSignature)) {
            currentPlayerId = pid;
            break;
          }
        } catch (EntityNotFoundException e) {
          // This should not be reached. If reached, there must be a bug in
          // logic.
          e.printStackTrace();
        }
      }

      List<Object> operations = (List<Object>) jsonMap.get(ContainerConstants.OPERATIONS);
      List<Operation> operationsOps = GameStateHelper.messageToOperationList(operations);

      EndGame endGame = null;
      AttemptChangeTokens attemptChangeTokens = null;
      // If the game is "turn" based, nextMovePlayerId will never be -1.
      long nextMovePlayerId = -1;
      for (Object op : operationsOps) {
        if (op instanceof EndGame) {
          endGame = (EndGame) op;
        } else if (op instanceof SetTurn) {
          nextMovePlayerId = Long.parseLong((String) ((SetTurn) op).getPlayerId());
        } else if (op instanceof AttemptChangeTokens) {
          attemptChangeTokens = (AttemptChangeTokens) op;
        }
      }

      try {
        MatchInfo mi = MatchInfo.getMatchInfoFromEntity(entity);

        // TODO These need to be modified at first place?
        mi.setPlayerThatHasTurn(nextMovePlayerId);
        if (attemptChangeTokens != null) {
          Map<Long, Long> newTokensInPot = Maps.newHashMap();
          Map<String, Integer> oldTokensInPot = attemptChangeTokens
              .getPlayerIdToNumberOfTokensInPot();
          for (String key : oldTokensInPot.keySet()) {
            newTokensInPot.put(Long.parseLong(key), (long) (oldTokensInPot.get(key)));
          }
          mi.setPlayerIdToNumberOfTokensInPot(newTokensInPot);
        }

        // Attention! This will also update MatchInfo.
        GameState newState = updateMatchInfoByOperations(mi, operations);

        if (endGame != null) {
          // Only update GameOverReason here.
          mi.setGameOverReason(ContainerConstants.OVER);

          // Update gameOverScores.
          Map<String, Integer> playerIdToScoreMap = endGame.getPlayerIdToScore();
          Map<Long, Integer> newPlayerIdToScoreMap = Maps.newHashMap();
          for (String key : playerIdToScoreMap.keySet()) {
            newPlayerIdToScoreMap.put(Long.parseLong(key), playerIdToScoreMap.get(key));
          }
          mi.setGameOverScores(newPlayerIdToScoreMap);

          // Update winInfo
          Map<String, Object> winInfo = new HashMap<String, Object>();
          winInfo.put(ContainerConstants.PLAYER_IDS, playerIds);
          long gameId = (Long) entity.getProperty(ContainerConstants.GAME_ID);
          winInfo.put(ContainerConstants.GAME_ID, gameId);
          winInfo.put(ContainerConstants.GAME_OVER_SCORES, newPlayerIdToScoreMap);
          if (mi.getPlayerIdToNumberOfTokensInPot() != null) {
            winInfo.put(ContainerConstants.PLAYER_ID_TO_NUMBER_OF_TOKENS_IN_POT,
                mi.getPlayerIdToNumberOfTokensInPot());
          }
          winInfo.put(ContainerConstants.MATCH_ID, matchId);

          EndGameDatabaseDriver.updateStats(winInfo);

        }

        // Write the object back to JSON formation.
        String rtnJsn = new ObjectMapper().writeValueAsString(mi);

        // Write back to Database.
        ContainerDatabaseDriver.updateMatchEntity(matchId, Utils.toMap(new JSONObject(rtnJsn)));

        // Response
        returnValue.put(ContainerConstants.MATCH_ID, String.valueOf(matchId));
        returnValue.put(ContainerConstants.STATE, newState
            .getStateForPlayerId(String.valueOf(currentPlayerId)));
        returnValue.put(
            ContainerConstants.LAST_MOVE,
            Message.listToMessage(GameStateHelper.getOperationsListForPlayer(operationsOps,
                newState.getVisibleTo(),
                String.valueOf(currentPlayerId))));

        // Write back to Database.
        ContainerDatabaseDriver.updateMatchEntity(matchId, Utils.toMap(new JSONObject(rtnJsn)));

        // Response through channel.
        for (long pid : playerIds) {
          if (pid == currentPlayerId) {
            continue;
          }
          ChannelService channelService = ChannelServiceFactory.getChannelService();
          String clientId = String.valueOf(pid);
          JSONObject returnValueChannel = new JSONObject();
          try {
            returnValueChannel.put(ContainerConstants.MATCH_ID, String.valueOf(matchId));
            returnValueChannel.put(ContainerConstants.STATE, newState
                .getStateForPlayerId(String.valueOf(pid)));
            returnValueChannel.put(
                ContainerConstants.LAST_MOVE,
                Message.listToMessage(GameStateHelper.getOperationsListForPlayer(operationsOps,
                    newState.getVisibleTo(),
                    String.valueOf(pid))));
          } catch (JSONException e1) {
          }
          channelService.sendMessage(new ChannelMessage(clientId, returnValueChannel.toString()));
        }
      } catch (JSONException e) {
        // This will be reached if there is something wrong with the formation
        // in Entity.
        e.printStackTrace();
      }
    } else {
      ContainerVerification.sendErrorMessage(
          resp, returnValue, ContainerConstants.NO_DATA_RECEIVED);
      return;
    }
    try {
      returnValue.write(resp.getWriter());
    } catch (JSONException e) {
    }
  }

  private GameState updateMatchInfoByOperations(MatchInfo mi, List<Object> operationsMapList) {
    List<Operation> operations = GameStateHelper.messageToOperationList(operationsMapList);

    GameState lastState;
    if (mi.getHistory().size() == 0) {
      // There is not GameState in History. Initial move.
      lastState = new GameState();
    } else {
      int lastIndex = mi.getHistory().size() - 1;
      lastState = mi.getHistory().get(lastIndex).getGameState();
    }

    GameState newState = lastState.copy();
    newState.makeMove(operations);

    GameStateHistoryItem gshi = new GameStateHistoryItem();
    gshi.setGameState(newState);
    gshi.setLastMove(getMapListFromOpsObjList(operationsMapList));

    mi.getHistory().add(gshi);

    return newState;
  }

  @SuppressWarnings("unchecked")
  private List<Map<String, Object>> getMapListFromOpsObjList(List<Object> operationsMapList) {
    List<Map<String, Object>> rtn = Lists.newArrayList();
    for (Object obj : operationsMapList) {
      rtn.add((Map<String, Object>) obj);
    }
    return rtn;
  }
}
