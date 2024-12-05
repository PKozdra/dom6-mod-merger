### UPX ###

**Przed optymalizacją od GraalVM**

`
upx.exe -4 "C:\repos\modmerger-compressed\*.exe"
`

`
upx.exe --best "C:\repos\modmerger-compressed\*.dll"
`

Przy zmniejszeniu rozmiaru exe trzeba używać poziomu kompresji 4, 5+ powoduje błędy w programie.

Jeżeli chodzi o .dll to dla nich --best jest bezpieczne.

Oryginalny rozmiar folderu: 70,4 MB (bajtów: 73 904 138)

Rozmiar folderu po kompresji: 33,0 MB (bajtów: 34 639 312)