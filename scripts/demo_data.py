#!/usr/bin/env python3
"""Demo data for MachineGuard Core API, loaded only through the public REST API.

Commands:
  seed       Create the demo zones, points, sensors and Safe Ranges, then backfill readings.
  live       Keep posting one reading per sensor every interval, like installed sensor nodes.
  excursion  Push the cold room out of its Safe Range for a few minutes, then bring it back.

Configuration (environment variables):
  MG_API_URL          Core API base URL (default http://localhost:8080)
  MG_ADMIN_EMAIL      ADMIN user that owns the demo data (required)
  MG_ADMIN_PASSWORD   Its password (required)
  MG_VIEWER_EMAIL     Optional read-only user to create during seed
  MG_VIEWER_PASSWORD  Its password (at least 12 characters)

Safe to run more than once: existing zones, points, sensors and readings are reused, never duplicated.
Requires Python 3.8+ and no third-party packages.
"""
import argparse
import hashlib
import json
import math
import os
import sys
import time
import urllib.error
import urllib.request
from datetime import datetime, timedelta, timezone

EM = "/api/v1/environmental-monitoring"
COLD_ROOM = "Cámara fría A"

# (name, description, {variable: (min, max)}, [(point, location, device code or None)])
ZONES = [
    (COLD_ROOM, "Almacén de lácteos", {"TEMPERATURE": (2, 8), "HUMIDITY": (35, 60)},
     [("Punto 1", "Estante B", "MG-ESP32-001"), ("Punto 2", "Junto a la puerta", "MG-ESP32-002")]),
    ("Almacén seco B", "Granos y harinas", {"TEMPERATURE": (18, 28), "HUMIDITY": (35, 60)},
     [("Punto 1", "Pasillo central", "MG-ESP32-003")]),
    # Left half configured on purpose: the class demo finishes it from the application.
    ("Muelle de carga", "Recepción de mercadería", {}, []),
]
# variable -> zone -> (typical value, daily swing, sensor noise)
PROFILES = {
    "TEMPERATURE": {COLD_ROOM: (4.6, 0.9, 0.35), "Almacén seco B": (22.5, 2.2, 0.4)},
    "HUMIDITY": {COLD_ROOM: (47.0, 3.0, 1.2), "Almacén seco B": (46.0, 4.0, 1.5)},
}
# Door left open: readings climb past 8 °C and recover.
EXCURSION_CURVE = [8.6, 9.8, 11.2, 10.4, 9.1]


class Api:
    def __init__(self):
        self.base = os.environ.get("MG_API_URL", "http://localhost:8080").rstrip("/")
        self.email = os.environ.get("MG_ADMIN_EMAIL")
        self.password = os.environ.get("MG_ADMIN_PASSWORD")
        if not self.email or not self.password:
            sys.exit("Set MG_ADMIN_EMAIL and MG_ADMIN_PASSWORD before running this script.")
        self.token = None
        self.organization_id = None

    def login(self):
        body = self._send("POST", "/api/v1/auth/login", {"email": self.email, "password": self.password}, auth=False)
        self.token = body["accessToken"]
        self.organization_id = body["organization"]["id"]

    def call(self, method, path, body=None):
        """Returns (status, json). Access tokens last minutes, so one 401 triggers a fresh login."""
        for attempt in (1, 2):
            try:
                return 200, self._send(method, path, body)
            except urllib.error.HTTPError as error:
                if error.code == 401 and attempt == 1:
                    self.login()
                    continue
                return error.code, None

    def _send(self, method, path, body=None, auth=True):
        headers = {"Content-Type": "application/json"}
        if auth:
            headers["Authorization"] = "Bearer " + self.token
        data = None if body is None else json.dumps(body).encode()
        request = urllib.request.Request(self.base + path, data, headers, method=method)
        with urllib.request.urlopen(request, timeout=30) as response:
            raw = response.read()
            return json.loads(raw) if raw else None


def must(result, what):
    status, body = result
    if status != 200:
        sys.exit(f"{what} failed with HTTP {status}")
    return body


def iso(moment):
    return moment.strftime("%Y-%m-%dT%H:%M:%SZ")


def reading(zone, point, variable, moment):
    """Plausible value that is stable for a given sensor and instant, so re-runs agree."""
    typical, swing, noise = PROFILES[variable][zone]
    seed = hashlib.sha256(f"{zone}|{point}|{variable}|{iso(moment)}".encode()).digest()
    jitter = (int.from_bytes(seed[:4], "big") / 0xFFFFFFFF - 0.5) * 2 * noise
    daily = math.sin((moment.hour + moment.minute / 60 - 9) / 24 * 2 * math.pi) * swing
    sign = 1 if variable == "TEMPERATURE" else -1  # warmer afternoons are also drier
    return round(typical + sign * daily + jitter, 2)


def post_reading(api, zone, point, sensor_id, variable, value, moment):
    status, _ = api.call("POST", EM + "/measurements", {
        "monitoringZoneId": zone["id"], "monitoringPointId": point["id"], "sensorNodeId": sensor_id,
        "environmentalVariable": variable, "measuredValue": value, "recordedAt": iso(moment)})
    if status not in (200, 409):  # 409: that sensor already reported at that instant
        sys.exit(f"Reading for {zone['name']} / {point['name']} failed with HTTP {status}")
    return status == 200


def load_zones(api):
    return {zone["name"]: zone for zone in must(api.call("GET", EM + "/zones"), "Listing zones")}


def sensors_of(zone):
    """[(point, sensor id or None)] for every point of the zone."""
    by_point = {sensor["monitoringPointId"]: sensor["id"] for sensor in zone["sensors"]}
    return [(point, by_point.get(point["id"])) for point in zone["points"]]


def latest_by_series(zone):
    return {(m["monitoringPointId"], m["environmentalVariable"]): m for m in zone["latestMeasurements"]}


def parse(moment):
    return datetime.fromisoformat(moment.replace("Z", "+00:00"))


def configure(api):
    zones = load_zones(api)
    for name, description, ranges, points in ZONES:
        zone = zones.get(name)
        if zone is None:
            zone = must(api.call("POST", EM + "/zones", {"name": name, "description": description}), f"Creating {name}")
            print(f"  zona creada: {name}")
        base = f"{EM}/zones/{zone['id']}"
        for variable, (minimum, maximum) in ranges.items():
            must(api.call("PUT", f"{base}/thresholds/{variable}", {"minimumValue": minimum, "maximumValue": maximum}),
                 f"Safe Range of {name}")
        known_points = {point["name"]: point for point in zone["points"]}
        known_codes = {sensor["deviceCode"] for sensor in zone["sensors"]}
        for point_name, location, device_code in points:
            point = known_points.get(point_name) or must(
                api.call("POST", base + "/points", {"name": point_name, "location": location}), f"Point of {name}")
            if device_code and device_code not in known_codes:
                status, _ = api.call("POST", f"{base}/points/{point['id']}/sensors",
                                     {"deviceCode": device_code, "samplingIntervalSeconds": 60})
                if status == 409:  # device codes are unique across the whole installation
                    print(f"  aviso: el código {device_code} ya está en uso; {point_name} queda sin sensor")
                elif status != 200:
                    sys.exit(f"Sensor {device_code} failed with HTTP {status}")
    return load_zones(api)


def create_viewer(api):
    email, password = os.environ.get("MG_VIEWER_EMAIL"), os.environ.get("MG_VIEWER_PASSWORD")
    if not email or not password:
        return
    status, _ = api.call("POST", f"/api/v1/organizations/{api.organization_id}/users",
                         {"email": email, "password": password, "fullName": "Usuario Lector Demo", "role": "VIEWER"})
    print("  usuario lector:", "creado" if status == 200 else "ya existía" if status == 409 else f"HTTP {status}")


def seed(api, hours, minutes):
    print("Configurando zonas…")
    create_viewer(api)
    zones = configure(api)
    step = timedelta(minutes=minutes)
    now = datetime.now(timezone.utc).replace(second=0, microsecond=0)
    now -= timedelta(minutes=now.minute % minutes)
    slots = [now - step * index for index in range(int(hours * 60 / minutes), 0, -1)]
    # A closed excursion a few hours back gives the reports something to show.
    excursion_start = len(slots) - int(5 * 60 / minutes)
    created = 0
    print(f"Cargando {hours} h de mediciones cada {minutes} min…")
    for index, moment in enumerate(slots):  # oldest first: the excursion lifecycle depends on order
        for name, zone in zones.items():
            latest = latest_by_series(zone)
            for point, sensor_id in sensors_of(zone):
                for variable in PROFILES:
                    if name not in PROFILES[variable]:
                        continue
                    previous = latest.get((point["id"], variable))
                    if previous and parse(previous["recordedAt"]) >= moment:
                        continue  # already loaded by an earlier run
                    value = reading(name, point["name"], variable, moment)
                    offset = index - excursion_start
                    if (name, point["name"], variable) == (COLD_ROOM, "Punto 1", "TEMPERATURE") \
                            and 0 <= offset < len(EXCURSION_CURVE):
                        value = EXCURSION_CURVE[offset]
                    created += post_reading(api, zone, point, sensor_id, variable, value, moment)
    print(f"Listo: {created} mediciones nuevas.")


def live(api, seconds, hold):
    print(f"Simulando sensores cada {seconds} s. Ctrl+C para detener.")
    while True:
        now = datetime.now(timezone.utc).replace(microsecond=0)
        for name, zone in load_zones(api).items():
            ranges = {t["environmentalVariable"]: t for t in zone["thresholds"]}
            latest = latest_by_series(zone)
            for point, sensor_id in sensors_of(zone):
                for variable in PROFILES:
                    if name not in PROFILES[variable] or variable not in ranges:
                        continue
                    previous = latest.get((point["id"], variable))
                    out = previous and not (ranges[variable]["minimumValue"] <= previous["measuredValue"]
                                            <= ranges[variable]["maximumValue"])
                    # Someone is demonstrating an excursion: leave it open instead of restoring it at once.
                    if out and (now - parse(previous["recordedAt"])).total_seconds() < hold:
                        continue
                    post_reading(api, zone, point, sensor_id, variable, reading(name, point["name"], variable, now), now)
        time.sleep(seconds)


def excursion(api, seconds):
    zone = load_zones(api).get(COLD_ROOM)
    if zone is None or not zone["points"]:
        sys.exit(f"Run 'seed' first: {COLD_ROOM} is not configured.")
    point, sensor_id = sensors_of(zone)[0]
    for value in EXCURSION_CURVE + [6.2]:
        now = datetime.now(timezone.utc).replace(microsecond=0)
        post_reading(api, zone, point, sensor_id, "TEMPERATURE", value, now)
        print(f"  {COLD_ROOM} · {point['name']}: {value} °C", "(fuera de rango)" if value > 8 else "(normal)")
        if value > 8:
            time.sleep(seconds)


def main():
    parser = argparse.ArgumentParser(description="MachineGuard demo data")
    commands = parser.add_subparsers(dest="command", required=True)
    seeding = commands.add_parser("seed", help="configure demo zones and backfill readings")
    seeding.add_argument("--hours", type=float, default=24)
    seeding.add_argument("--interval-minutes", type=int, default=10)
    running = commands.add_parser("live", help="keep posting readings like installed sensors")
    running.add_argument("--interval-seconds", type=int, default=60)
    running.add_argument("--hold-seconds", type=int, default=300,
                         help="how long an out-of-range reading is left alone before normal readings resume")
    breaking = commands.add_parser("excursion", help="take the cold room out of range and back")
    breaking.add_argument("--step-seconds", type=int, default=20)
    args = parser.parse_args()

    api = Api()
    try:
        api.login()
    except urllib.error.HTTPError as error:
        sys.exit(f"Login failed with HTTP {error.code}. Check MG_ADMIN_EMAIL and MG_ADMIN_PASSWORD.")
    except urllib.error.URLError as error:
        sys.exit(f"Cannot reach {api.base}: {error.reason}")
    try:
        if args.command == "seed":
            seed(api, args.hours, args.interval_minutes)
        elif args.command == "live":
            live(api, args.interval_seconds, args.hold_seconds)
        else:
            excursion(api, args.step_seconds)
    except KeyboardInterrupt:
        print("\nDetenido.")


if __name__ == "__main__":
    main()
