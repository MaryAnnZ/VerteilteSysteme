
Chatserver: hat einen TCP und einen UDP listener, legt eine neue HashMap
	fuer mit allen usern an

TCP listener: wartet auf clients, die sich mit dem Server verbinden
	wollen und legt fuer jeden Client einen neuen ClientHandler an;
	alle ClientHandlers werden in einer HashMap gespeichert, um
	oeffentliche Nachrichten an alle zu schicken

ClientHandler: bearbeitet die Anfragen des Clients

Client: nimmt die Befehle entgegen und gibt die Rueckgaben des Servers aus


der send Befehlt funktioniert nicht immer, da er manchmal blokiert und ist
deshalb in der Client run Methode auskommentiert
ich hab versucht einen neuen thread dafuer zu erstellen, jedoch konnte ich
die responses nicht mehr zuordnen, die ich zB fuer den login brauche