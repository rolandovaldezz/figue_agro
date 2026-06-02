# ============================================================
# frontend - Dockerfile
# ============================================================
# Servidor estático con NGINX. Sirve los archivos HTML/JS/CSS
# que cargan Bootstrap desde CDN y consumen el API Gateway.
# ============================================================
# Este Dockerfile NO compila nada: solo empaqueta archivos estaticos en NGINX.

# Imagen base de NGINX (Alpine = liviana) que sirve contenido web estatico
FROM nginx:1.27-alpine

# Quitamos el contenido por defecto de NGINX
# Borra la pagina de bienvenida que trae la imagen para dejar limpio el directorio web
RUN rm -rf /usr/share/nginx/html/*

# Copiamos nuestros archivos estáticos
# Lleva el HTML/JS/CSS del proyecto a la carpeta publica que sirve NGINX
COPY . /usr/share/nginx/html/

# Instalamos nuestra configuracion de NGINX (sirve estaticos + proxy /api -> gateway)
# Reemplaza la configuracion por defecto del servidor web
COPY nginx-default.conf /etc/nginx/conf.d/default.conf

# Quitamos archivos que no deben servirse
# Elimina el Dockerfile, el .dockerignore y la config (ya copiada) para que no queden expuestos en la web
RUN rm -f /usr/share/nginx/html/Dockerfile /usr/share/nginx/html/.dockerignore /usr/share/nginx/html/nginx-default.conf

# Puertos internos: 443 (HTTPS, principal) y 80 (solo redirige a HTTPS)
EXPOSE 443 80

# NGINX corre como entrypoint por defecto
# La imagen base ya define el comando de arranque, por eso no se declara ENTRYPOINT aqui
