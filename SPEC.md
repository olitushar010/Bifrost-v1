# Bifrost v1.0 Specifications

## Ingestion Endpoint
- **Port:** 8080
- **Method:** POST
- **Route:** /events
## Payload Contract
```json
{
    "eventId": "123e4567-e89b-12d3",
    "eventType": "USER_CLICK",
    "timestamp": "2026-09-04T07:38:27Z",
    "payload": {
        "buttonName": "submit_order"
    }
}

