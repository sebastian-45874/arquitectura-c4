import { DatePipe, JsonPipe } from '@angular/common';
import { HttpClient, HttpErrorResponse, provideHttpClient } from '@angular/common/http';
import { Component, inject, provideZonelessChangeDetection, signal } from '@angular/core';
import { bootstrapApplication } from '@angular/platform-browser';
import { firstValueFrom } from 'rxjs';

interface Contribuyente {
  ruc: string;
  razonSocial: string;
  tipoContribuyente: string;
  estado: string;
}

interface Matricula {
  placa: string;
  vehiculo: Record<string, unknown>;
  rubros: unknown;
}

interface Licencia {
  puntos: number;
  consultadoEn: string;
  fuente: 'ANT' | 'CACHE';
  vigente: boolean;
}

@Component({
  selector: 'portal-consultas',
  imports: [DatePipe, JsonPipe],
  templateUrl: './portal.html'
})
class PortalConsultas {
  private readonly http = inject(HttpClient);
  readonly contribuyente = signal<Contribuyente | null>(null);
  readonly matricula = signal<Matricula | null>(null);
  readonly licencia = signal<Licencia | null>(null);
  readonly pendiente = signal<'ruc' | 'placa' | 'cedula' | null>(null);
  readonly error = signal<{ consulta: 'ruc' | 'placa' | 'cedula'; mensaje: string } | null>(null);

  async consultarRuc(ruc: string): Promise<void> {
    this.contribuyente.set(null);
    this.matricula.set(null);
    this.licencia.set(null);
    await this.consultar('ruc', async () => {
      this.contribuyente.set(await firstValueFrom(
        this.http.get<Contribuyente>('/api/sri/ruc/detalle', { params: { ruc } })
      ));
    });
  }

  async consultarPlaca(placa: string): Promise<void> {
    this.matricula.set(null);
    await this.consultar('placa', async () => {
      this.matricula.set(await firstValueFrom(
        this.http.get<Matricula>('/api/sri/vehiculo/matricula', { params: { placa } })
      ));
    });
  }

  async consultarLicencia(): Promise<void> {
    const persona = this.contribuyente();
    if (!persona) return;
    const cedula = persona.ruc.slice(0, 10);
    this.licencia.set(null);
    await this.consultar('cedula', async () => {
      this.licencia.set(await firstValueFrom(
        this.http.post<Licencia>('/api/ant/licencia/puntos', { cedula })
      ));
    });
  }

  private async consultar(tipo: 'ruc' | 'placa' | 'cedula', accion: () => Promise<void>): Promise<void> {
    this.error.set(null);
    this.pendiente.set(tipo);
    try {
      await accion();
    } catch (error) {
      const status = error instanceof HttpErrorResponse ? error.status : 0;
      let mensaje: string;
      if (status === 400) mensaje = 'Revisa el formato del dato ingresado.';
      else if (status === 404) mensaje = tipo === 'ruc' ? 'El RUC no existe.' : 'La placa no existe.';
      else if (status === 422) mensaje = 'El RUC no corresponde a una persona natural.';
      else mensaje = tipo === 'cedula'
        ? 'No se pudieron obtener los puntos de la ANT y no hay un resultado guardado.'
        : 'No se pudo completar la consulta al SRI.';
      this.error.set({ consulta: tipo, mensaje });
    } finally {
      this.pendiente.set(null);
    }
  }
}

bootstrapApplication(PortalConsultas, {
  providers: [provideHttpClient(), provideZonelessChangeDetection()]
}).catch(error => console.error(error));
