# -*- coding: utf-8 -*-
# Author: Michael Fischer
# Company: LGV
#
# Program: 
#
#
# ----------------------------------------------------------------------------------------------------------------------
# Module
from __future__ import print_function, division
import psycopg2
import time
from psycopg2.extensions import ISOLATION_LEVEL_AUTOCOMMIT
from threading import Thread

# ----------------------------------------------------------------------------------------------------------------------
# Variables


# Datenbankwerte
IP_adresse = "127.0.0.1"
port = "5432"
db_name = "sensorthingsTest"
#nutzername = "verkehr_c"
nutzername="sensorthings"
passwort = "ChangeMe"
# Connection String für Verbuindung zur Datenbank
conn_string = "hostaddr='" + IP_adresse + "' port='" + port + "' dbname='" + db_name + "' user='" + nutzername + "' password='" + passwort + "'"
anzahl_insert = 250
anzahl_threads = 4
anzahl_loops = 10

# ----------------------------------------------------------------------------------------------------------------------
# Functions

def log(string):
    print(time.strftime("%Y-%m-%d-%H-%M-%S", time.localtime()) + ": " + str(string))

def db_connection(conn_string1):
    connection = psycopg2.connect(conn_string1)
    connection.set_isolation_level(ISOLATION_LEVEL_AUTOCOMMIT)
    # cursor = connection.cursor()
    return connection

def loopinsert(conn_string2):
    con = db_connection(conn_string2)
    cursor1 = con.cursor()
    for x in range(anzahl_insert):
        cursor1.execute("""insert into "OBSERVATIONS" ("PHENOMENON_TIME_START","PHENOMENON_TIME_END", "RESULT_NUMBER","RESULT_STRING", "DATASTREAM_ID","FEATURE_ID","RESULT_TYPE") values (now(),now(),1,'1', 1455,853,0);""")
    cursor1.close()
    con.close()
# ----------------------------------------------------------------------------------------------------------------------
# Main Program

dauer = 0

for l in range(anzahl_loops):
    threads = []

    for x in range(anzahl_threads):
        thread = Thread(target=loopinsert, args=[conn_string])
        threads.append(thread)

    start = int(round(time.time() * 1000))

    for i in threads:
        i.start()

    for a in threads:
        a.join()

    end = int(round(time.time() * 1000))
    final = end - start
    dauer += final
    print(str(final))

durchschnitt = dauer/anzahl_loops
print("Durchschnitt:" + str(durchschnitt))
