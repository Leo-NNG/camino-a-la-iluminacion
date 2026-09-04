// Datos compartidos de las parroquias — usados por mapa.html e iglesia.html
const IGLESIAS = [
  {
    id: "san-jose",
    nombre: "Parroquia San José",
    siglas: "SJ",
    direccion: "Av. Los Álamos 245, Chiclayo",
    distancia: "1.2 km",
    lat: -6.7701,
    lng: -79.8380,
    sacramentos: ["Bautizo", "Matrimonio", "Primera Comunión"],
    foto: "https://images.pexels.com/photos/3025593/pexels-photo-3025593.jpeg?auto=compress&cs=tinysrgb&w=900",
    fotoInterior: "https://images.pexels.com/photos/7520352/pexels-photo-7520352.jpeg?auto=compress&cs=tinysrgb&w=900",
    horarioMisas: [
      { dia: "Lunes a viernes", detalle: "7:00 a.m. y 7:00 p.m." },
      { dia: "Sábado", detalle: "8:00 a.m. y 6:00 p.m." },
      { dia: "Domingo", detalle: "7, 9, 11 a.m. y 7 p.m." }
    ],
    tramites: "Lunes a viernes, 9:00 a.m. a 1:00 p.m., oficina parroquial."
  },
  {
    id: "santa-fe",
    nombre: "Parroquia Santa Fe",
    siglas: "SF",
    direccion: "Calle Real 512, Chiclayo",
    distancia: "2.8 km",
    lat: -6.7620,
    lng: -79.8450,
    sacramentos: ["Bautizo", "Confirmación"],
    foto: "https://images.pexels.com/photos/5139675/pexels-photo-5139675.jpeg?cs=tinysrgb&w=900",
    fotoInterior: "https://images.pexels.com/photos/208277/pexels-photo-208277.jpeg?auto=compress&cs=tinysrgb&w=900",
    horarioMisas: [
      { dia: "Lunes a viernes", detalle: "6:30 a.m. y 6:30 p.m." },
      { dia: "Sábado", detalle: "5:00 p.m. (vigilia)" },
      { dia: "Domingo", detalle: "8, 10 a.m. y 6 p.m." }
    ],
    tramites: "Martes y jueves, 3:00 p.m. a 6:00 p.m., casa parroquial."
  },
  {
    id: "carmen",
    nombre: "Parroquia Nuestra Señora del Carmen",
    siglas: "NS",
    direccion: "Jr. Las Palmeras 88, Chiclayo",
    distancia: "3.5 km",
    lat: -6.7800,
    lng: -79.8300,
    sacramentos: ["Matrimonio", "Primera Comunión", "Confirmación"],
    foto: "https://images.pexels.com/photos/18750283/pexels-photo-18750283/free-photo-of-photo-of-a-small-church-and-palm-trees.jpeg?cs=tinysrgb&w=900",
    fotoInterior: "https://images.pexels.com/photos/9153530/pexels-photo-9153530.jpeg?auto=compress&cs=tinysrgb&w=900",
    horarioMisas: [
      { dia: "Lunes a viernes", detalle: "7:30 a.m." },
      { dia: "Sábado", detalle: "6:00 p.m." },
      { dia: "Domingo", detalle: "9 a.m., 12 p.m. y 7 p.m." }
    ],
    tramites: "Lunes a sábado, 10:00 a.m. a 1:00 p.m."
  },
  {
    id: "sagrado-corazon",
    nombre: "Capilla Sagrado Corazón",
    siglas: "SC",
    direccion: "Av. Grau 1420, Chiclayo",
    distancia: "4.1 km",
    lat: -6.7550,
    lng: -79.8500,
    sacramentos: ["Bautizos comunitarios"],
    foto: "https://images.pexels.com/photos/19623130/pexels-photo-19623130/free-photo-of-exterior-of-a-church.jpeg?auto=compress&cs=tinysrgb&w=900",
    fotoInterior: "https://images.pexels.com/photos/27872483/pexels-photo-27872483/free-photo-of-a-church-with-a-cross-in-the-middle-of-it.jpeg?auto=compress&cs=tinysrgb&w=900",
    horarioMisas: [
      { dia: "Sábado", detalle: "4:00 p.m." },
      { dia: "Domingo", detalle: "10:00 a.m." }
    ],
    tramites: "Solo domingos, 9:00 a.m. a 10:00 a.m., a la entrada de la capilla."
  }
];
