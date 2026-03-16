# 1. Opis biznesowy api
API służy do rejestracji transakcji sprzedaży i zwortu. W celu naliczenia punktów na koncie klienta. 
Klient po osiagnięciu pewnego poziomu punktów może wymienić je na kupony. 
Biznes z poziomu panelu admina chce móc sterować przeliczaniem wydanych kwot na punkty robic czasowe promocje np : 1 zł to standardowo 1 pkt, ale jeśli klient wstrzeli się w czas trwania promocji to naliczać punkty np 4 pkt za 1 zł 
Punkty do salda dodają się po 30 dniach od daty zakupy. Zanim przejda w stan kiedy można je wykorzystać oczekująna upłyniecie czasu. 
Klient widzi punkty które oczeklują i które są naliczone. Po zwrocie punkty są odejmowane od konta klienta jesli była jakaś akcja promocyjna to muszą się odejmować tak jak się naliczyły.


Jeśli punkty są już jako do wykorzystania są ważne 365 dni od daty zakupu.

