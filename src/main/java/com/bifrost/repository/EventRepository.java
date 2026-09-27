package com.bifrost.repository;

import com.bifrost.db.DatabaseConnection;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.postgresql.util.PGobject;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;

import com.bifrost.dto.EventPayload;

public class EventRepository {
    private static final ObjectMapper mapper = new ObjectMapper();

  public int insertEvent(EventPayload event) throws JsonProcessingException , SQLException {
      PGobject obj = new PGobject();
    try (Connection conn = DatabaseConnection.getConnection();
        PreparedStatement ptsmt =
            conn.prepareStatement(
                "INSERT INTO events (event_id,event_type,payload) VALUES (?, ?,?)"); ) {
      obj.setType("jsonb");
      obj.setValue(mapper.writeValueAsString(event.getPayload()));
      ptsmt.setObject(1, event.getEventId());
      ptsmt.setString(2, event.getEventType());
      ptsmt.setObject(3, obj);

      return ptsmt.executeUpdate();
    }
  }
}
